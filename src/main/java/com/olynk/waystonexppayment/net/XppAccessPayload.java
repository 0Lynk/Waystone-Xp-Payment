package com.olynk.waystonexppayment.net;

import com.olynk.waystonexppayment.WaystoneXpPaymentMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server request to change a tank's access list (and to re-request its state).
 *
 * <p>Kept intentionally dumb: the client only names the action and the entry, the server revalidates
 * permission and then answers with a fresh {@link XppStatePayload}.
 */
public record XppAccessPayload(BlockPos pos, Action action, String value) implements CustomPacketPayload {

    public enum Action {
        SET_MODE,
        ADD_PLAYER,
        REMOVE_PLAYER,
        ADD_TEAM,
        REMOVE_TEAM,
        REFRESH
    }

    public static final CustomPacketPayload.Type<XppAccessPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    WaystoneXpPaymentMod.MOD_ID, "tank_access"));

    public static final StreamCodec<RegistryFriendlyByteBuf, XppAccessPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.pos());
                buffer.writeEnum(payload.action());
                buffer.writeUtf(payload.value(), 64);
            },
            buffer -> new XppAccessPayload(buffer.readBlockPos(), buffer.readEnum(Action.class), buffer.readUtf(64)));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
