package com.olynk.waystonexppayment.net;

import java.util.ArrayList;
import java.util.List;

import com.olynk.waystonexppayment.WaystoneXpPaymentMod;

import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
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

    /**
     * What the server says about this player's right to pay from the tank at one slot.
     *
     * <p>Three states, because two cannot express the case the feature exists for: a tank whose chunk was never
     * visited and whose access list cannot be checked is neither usable nor unusable, and calling it either one
     * would lie to the player.
     */
    public enum Access {
        /** The tank's access list admits this player; the amount beside it is real. */
        ALLOWED,
        /** The server established that this player may not pay from that tank. No amount is reported. */
        DENIED,
        /** Nothing decisive is known: no team mod answered, the chunk was never seen, or the switch is off. */
        UNKNOWN
    }

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
    public static final int MAX_HINTS = XppWireLimits.MAX_HINTS;

    private static final Access[] ACCESS_VALUES = Access.values();

    /**
     * One slot's answer.
     *
     * @param poolXp XP the tank can currently pay, {@code 0} when it exists but holds nothing usable or when the
     *               player may not use it, {@link #NO_TANK} when there is no tank at all, or {@link #UNKNOWN}
     *               when the server could not look (chunk not loaded and nothing recorded)
     * @param mbPerXp millibuckets per XP the server used for {@code poolXp}
     * @param access whether this player may pay from that tank at all
     */
    public record Hint(BlockPos pos, int poolXp, int mbPerXp, Access access) {

        public static Hint none(BlockPos pos) {
            return new Hint(pos, NO_TANK, 0, Access.UNKNOWN);
        }

        public static Hint unknown(BlockPos pos) {
            return new Hint(pos, UNKNOWN, 0, Access.UNKNOWN);
        }

        /** "There is a tank you may not use": the position is reported, the amount deliberately is not. */
        public static Hint denied(BlockPos pos) {
            return new Hint(pos, 0, 0, Access.DENIED);
        }

        public static Hint allowed(BlockPos pos, int poolXp, int mbPerXp) {
            return new Hint(pos, poolXp, mbPerXp, Access.ALLOWED);
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

        /**
         * True when the client may treat this slot as a payer candidate.
         *
         * <p>Only two answers rule a slot out: {@link Access#DENIED}, because the server established that this
         * player may not spend from it, and {@link #NO_TANK}, because the server looked and found nothing. An
         * {@link #UNKNOWN} pool stays permissive - an unvisited far-away tank must not grey the button out, which
         * is the behaviour this whole feature exists to remove - and a tank that is merely empty stays a payer so
         * the arithmetic, not this predicate, decides.
         */
        public boolean mayPay() {
            return access != Access.DENIED && poolXp != NO_TANK;
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
                    writeAccess(buffer, hint.access());
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
                    hints.add(new Hint(buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt(),
                            readAccess(buffer)));
                }
                return new XppTankHintPayload(dimension, List.copyOf(hints));
            });

    /** Builds a payload, dropping any hint past {@link #MAX_HINTS}. */
    public static XppTankHintPayload of(ResourceLocation dimension, List<Hint> hints) {
        List<Hint> capped = hints.size() <= MAX_HINTS ? hints : List.copyOf(hints.subList(0, MAX_HINTS));
        return new XppTankHintPayload(dimension, capped);
    }

    /**
     * Reads the tri-state, refusing a value no version of this mod ever wrote.
     *
     * <p>Takes the plain {@code FriendlyByteBuf} so the tri-state can be tested without a registry-aware buffer:
     * this one byte is what decides whether a client treats a tank as usable.
     */
    static Access readAccess(FriendlyByteBuf buffer) {
        int raw = buffer.readByte();
        if (raw < 0 || raw >= ACCESS_VALUES.length) {
            throw new DecoderException("Unknown tank hint access: " + raw);
        }
        return ACCESS_VALUES[raw];
    }

    static void writeAccess(FriendlyByteBuf buffer, Access access) {
        buffer.writeByte(access.ordinal());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
