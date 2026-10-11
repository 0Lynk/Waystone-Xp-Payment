package com.olynk.waystonexppayment.net;

import java.util.UUID;

import javax.annotation.Nullable;

import com.olynk.waystonexppayment.WaystoneXpPaymentMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server request to change a tank's access list (and to re-request its state).
 *
 * <p>Identity travels as a {@link UUID} and nothing else: {@code label} is a human-readable name carried only
 * so the server log says which entry changed. The server never parses identity out of it.
 */
public record XppAccessPayload(BlockPos pos, Action action, @Nullable UUID target, String label)
        implements CustomPacketPayload {

    public enum Action {
        SET_MODE,
        ADD_PLAYER,
        REMOVE_PLAYER,
        ADD_TEAM,
        REMOVE_TEAM,
        REFRESH
    }

    /**
     * Stand-in for the actions that address no entry. A signed zero UUID is unambiguous because no
     * game-profile id and no FTB team id is ever all zeros.
     */
    public static final UUID NO_TARGET = new UUID(0L, 0L);

    public static final CustomPacketPayload.Type<XppAccessPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    WaystoneXpPaymentMod.MOD_ID, "tank_access"));

    public static final StreamCodec<RegistryFriendlyByteBuf, XppAccessPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.pos());
                buffer.writeEnum(payload.action());
                buffer.writeBoolean(payload.target() != null);
                if (payload.target() != null) {
                    UUIDUtil.STREAM_CODEC.encode(buffer, payload.target());
                }
                buffer.writeUtf(payload.label(), 64);
            },
            buffer -> {
                BlockPos pos = buffer.readBlockPos();
                Action action = buffer.readEnum(Action.class);
                UUID target = buffer.readBoolean() ? UUIDUtil.STREAM_CODEC.decode(buffer) : NO_TARGET;
                return new XppAccessPayload(pos, action, target, buffer.readUtf(64));
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
