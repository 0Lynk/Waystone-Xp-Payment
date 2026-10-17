package com.olynk.waystonexppayment.net;

import java.util.ArrayList;
import java.util.List;

import com.olynk.waystonexppayment.WaystoneXpPaymentMod;

import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client answer to {@link XppTankHintQueryPayload}: one hint per slot the client asked about.
 *
 * <p>Every queried slot gets an entry, including the ones with no tank, so the client can tell "the server looked
 * and there is nothing here" apart from "no answer yet". That difference is the whole point: the first must grey
 * the button out, the second must stay optimistic and let the click through to the server.
 *
 * <p>Contents are a snapshot. The server still decides on the actual payment, and the client re-reads the live
 * block entity whenever the chunk happens to be loaded anyway.
 */
public record XppTankHintPayload(ResourceLocation dimension, List<Hint> hints) implements CustomPacketPayload {

    /** {@code poolXp} for "the server knows there is no tank at this slot". */
    public static final int NO_TANK = -1;

    /**
     * {@code poolXp} for "the server cannot tell": the chunk is not loaded and no earlier visit recorded a pool.
     *
     * <p>Kept distinct from {@link #NO_TANK} on purpose. This is the case the whole feature is about, so the
     * client must not read it as "grey": the server is allowed to load that chunk when the player actually
     * travels there, and it may well find a tank.
     */
    public static final int UNKNOWN = -2;

    /** Wire cap, matching {@link XppTankHintQueryPayload#MAX_SLOTS}. */
    public static final int MAX_HINTS = XppTankHintQueryPayload.MAX_SLOTS;

    /**
     * One slot's answer.
     *
     * @param poolXp XP the tank can currently pay, {@code 0} when it exists but holds nothing usable,
     *               {@link #NO_TANK} when there is no tank at all, or {@link #UNKNOWN} when the server could not
     *               look (chunk not loaded and nothing recorded)
     * @param mbPerXp millibuckets per XP the server used for {@code poolXp}
     */
    public record Hint(BlockPos pos, int poolXp, int mbPerXp) {

        public static Hint none(BlockPos pos) {
            return new Hint(pos, NO_TANK, 0);
        }

        public static Hint unknown(BlockPos pos) {
            return new Hint(pos, UNKNOWN, 0);
        }

        public boolean hasTank() {
            return poolXp != NO_TANK && poolXp != UNKNOWN;
        }

        /** True when the server positively established that nothing sits at this slot. */
        public boolean knownAbsent() {
            return poolXp == NO_TANK;
        }

        public boolean known() {
            return poolXp != UNKNOWN;
        }
    }

    public static final CustomPacketPayload.Type<XppTankHintPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    WaystoneXpPaymentMod.MOD_ID, "tank_hints"));

    public static final StreamCodec<RegistryFriendlyByteBuf, XppTankHintPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeResourceLocation(payload.dimension());
                buffer.writeVarInt(payload.hints().size());
                for (Hint hint : payload.hints()) {
                    buffer.writeBlockPos(hint.pos());
                    buffer.writeVarInt(hint.poolXp());
                    buffer.writeVarInt(hint.mbPerXp());
                }
            },
            buffer -> {
                ResourceLocation dimension = buffer.readResourceLocation();
                int size = buffer.readVarInt();
                if (size < 0 || size > MAX_HINTS) {
                    // Same reasoning as the query: refuse rather than desynchronise the stream.
                    throw new DecoderException("Tank hint count out of range: " + size);
                }
                List<Hint> hints = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    hints.add(new Hint(buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt()));
                }
                return new XppTankHintPayload(dimension, List.copyOf(hints));
            });

    /** Builds a payload, dropping any hint past {@link #MAX_HINTS}. */
    public static XppTankHintPayload of(ResourceLocation dimension, List<Hint> hints) {
        List<Hint> capped = hints.size() <= MAX_HINTS ? hints : List.copyOf(hints.subList(0, MAX_HINTS));
        return new XppTankHintPayload(dimension, capped);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
