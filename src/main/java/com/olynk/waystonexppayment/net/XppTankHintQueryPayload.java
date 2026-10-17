package com.olynk.waystonexppayment.net;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.olynk.waystonexppayment.WaystoneXpPaymentMod;

import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server: "these waystones are in the list I am drawing - is there a pool below any of them?"
 *
 * <p>Exists because the client cannot answer that for itself: a destination whose chunk is not loaded has no
 * block entity to inspect, and remembering the position is not the same as knowing its contents. Without the
 * answer the list either greys out a trip the server would have paid for, or promises a payment that then falls
 * back to the player's own XP - both are the "quote that lies" problem this mod is supposed to avoid.
 *
 * <p>{@code tankSlots} are the positions the client computed, not the waystone positions: the slot rule (one
 * block below, or two for a double-block waystone) stays on one side of the wire.
 */
public record XppTankHintQueryPayload(ResourceLocation dimension, List<BlockPos> tankSlots)
        implements CustomPacketPayload {

    /**
     * Wire cap on one query.
     *
     * <p>A waystone list is a scrolling viewport, not a full dump, and every slot past this one buys nothing a
     * player can see - but it does buy the server a chunk lookup each. The client sends more entries in a
     * follow-up packet instead of one unbounded one.
     */
    public static final int MAX_SLOTS = XppWireLimits.MAX_HINTS;

    public static final CustomPacketPayload.Type<XppTankHintQueryPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    WaystoneXpPaymentMod.MOD_ID, "tank_hint_query"));

    public static final StreamCodec<RegistryFriendlyByteBuf, XppTankHintQueryPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeResourceLocation(payload.dimension());
                        buffer.writeVarInt(payload.tankSlots().size());
                        for (BlockPos pos : payload.tankSlots()) {
                            buffer.writeBlockPos(pos);
                        }
                    },
                    buffer -> {
                        ResourceLocation dimension = buffer.readResourceLocation();
                        int size = buffer.readVarInt();
                        if (size < 0 || size > MAX_SLOTS) {
                            // Reading on regardless would leave the buffer mid-entry and corrupt every later
                            // payload on this connection, so the packet is refused instead.
                            throw new DecoderException("Tank hint slot count out of range: " + size);
                        }
                        List<BlockPos> slots = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            slots.add(buffer.readBlockPos());
                        }
                        return new XppTankHintQueryPayload(dimension, List.copyOf(slots));
                    });

    /** Builds a query, dropping duplicates and everything past {@link #MAX_SLOTS}. */
    public static XppTankHintQueryPayload of(ResourceLocation dimension, List<BlockPos> slots) {
        Set<BlockPos> unique = new LinkedHashSet<>();
        for (BlockPos pos : slots) {
            if (unique.size() >= MAX_SLOTS) {
                break;
            }
            unique.add(pos);
        }
        return new XppTankHintQueryPayload(dimension, List.copyOf(unique));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
