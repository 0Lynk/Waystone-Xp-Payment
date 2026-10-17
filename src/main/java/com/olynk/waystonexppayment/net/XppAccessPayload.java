package com.olynk.waystonexppayment.net;

import java.util.UUID;

import javax.annotation.Nullable;

import com.olynk.waystonexppayment.WaystoneXpPaymentMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server request to change a tank's access list (and to re-request its state or its team members).
 *
 * <p>Identity travels as a {@link UUID} and nothing else: {@code label} is a human-readable name carried only
 * so the server log says which entry changed - and, for {@link Action#REQUEST_MEMBERS}, so the server can
 * answer with the name it knows. The server never parses identity or intent out of it.
 *
 * <p>{@code requestId} is echoed back by {@link XppMembersPayload}: that is the only channel the answer can
 * travel on, and without it a reply to an earlier request cannot be told apart from this one's.
 */
public record XppAccessPayload(BlockPos pos, Action action, @Nullable Mode mode, @Nullable UUID target,
                               String label, long requestId) implements CustomPacketPayload {

    public enum Action {
        SET_MODE,
        ADD_PLAYER,
        REMOVE_PLAYER,
        ADD_TEAM,
        REMOVE_TEAM,
        REFRESH,
        REQUEST_MEMBERS
    }

    /**
     * Access list mode a {@link Action#SET_MODE} request asks for.
     *
     * <p>WHY a field of its own: the mode used to travel inside {@code label} as the literal {@code "blacklist"},
     * which made a permission rule depend on display text - any rewording or translation of that string would
     * have flipped the rule without a compile error. {@code label} is now only what the log prints.
     */
    public enum Mode {
        WHITELIST,
        BLACKLIST
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
                writeMode(buffer, payload.mode());
                buffer.writeBoolean(payload.target() != null);
                if (payload.target() != null) {
                    UUIDUtil.STREAM_CODEC.encode(buffer, payload.target());
                }
                buffer.writeUtf(XppWireLimits.text(payload.label()), XppWireLimits.MAX_TEXT_BYTES);
                buffer.writeLong(payload.requestId());
            },
            buffer -> {
                BlockPos pos = buffer.readBlockPos();
                Action action = buffer.readEnum(Action.class);
                Mode mode = readMode(buffer);
                UUID target = buffer.readBoolean() ? UUIDUtil.STREAM_CODEC.decode(buffer) : NO_TARGET;
                return new XppAccessPayload(pos, action, mode, target,
                        buffer.readUtf(XppWireLimits.MAX_TEXT_BYTES), buffer.readLong());
            });

    /**
     * Writes the mode slot: a presence flag plus the value.
     *
     * <p>Split out of the codec so the one field this version added can be exercised without a registry-aware
     * buffer - the mode's meaning is a permission rule, and a rule deserves a test that does not need a server.
     */
    static void writeMode(FriendlyByteBuf buffer, @Nullable Mode mode) {
        buffer.writeBoolean(mode != null);
        if (mode != null) {
            buffer.writeEnum(mode);
        }
    }

    @Nullable
    static Mode readMode(FriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readEnum(Mode.class) : null;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
