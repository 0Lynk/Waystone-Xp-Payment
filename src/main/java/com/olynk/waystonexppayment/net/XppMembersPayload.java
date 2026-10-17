package com.olynk.waystonexppayment.net;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.olynk.waystonexppayment.WaystoneXpPaymentMod;

import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server -> client answer to a "view this team's members" request.
 *
 * <p>Members can only come from the server: a client has no team mod of its own in general, and the tank's
 * access model treats a team as one opaque id, so nothing about its membership is derivable locally.
 *
 * <p>{@code requestId} pairs the answer with the request. Opening the panel twice in a row on the same team,
 * or retrying after a timeout, leaves the earlier reply in flight; without the id it would be indistinguishable
 * from the answer to the later request, and the panel would leave its loading state at the wrong moment.
 */
public record XppMembersPayload(BlockPos pos, UUID teamId, String teamLabel, byte status, long requestId,
                                List<Member> members) implements CustomPacketPayload {

    /**
     * Wire cap on the member list.
     *
     * <p>Not a team limit: past this the packet grows without bound on a server that lets anyone create teams,
     * and no five-row panel can show that many rows usefully anyway.
     */
    public static final int MAX_MEMBERS = XppWireLimits.MAX_LIST;

    /** One member row. {@code name} is the server's best label: a game profile name, or a UUID prefix. */
    public record Member(UUID id, String name, boolean online) {
    }

    public static final CustomPacketPayload.Type<XppMembersPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    WaystoneXpPaymentMod.MOD_ID, "tank_members"));

    public static final StreamCodec<RegistryFriendlyByteBuf, XppMembersPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.pos());
                UUIDUtil.STREAM_CODEC.encode(buffer, payload.teamId());
                buffer.writeUtf(XppWireLimits.text(payload.teamLabel()), XppWireLimits.MAX_TEXT_BYTES);
                buffer.writeByte(payload.status());
                buffer.writeLong(payload.requestId());
                buffer.writeVarInt(payload.members().size());
                for (Member member : payload.members()) {
                    UUIDUtil.STREAM_CODEC.encode(buffer, member.id());
                    buffer.writeUtf(XppWireLimits.text(member.name()), XppWireLimits.MAX_TEXT_BYTES);
                    buffer.writeBoolean(member.online());
                }
            },
            buffer -> {
                BlockPos pos = buffer.readBlockPos();
                UUID teamId = UUIDUtil.STREAM_CODEC.decode(buffer);
                String teamLabel = buffer.readUtf(64);
                byte status = buffer.readByte();
                long requestId = buffer.readLong();
                int size = buffer.readVarInt();
                if (size < 0 || size > MAX_MEMBERS) {
                    // Reading on regardless would leave the buffer mid-entry and corrupt every later payload
                    // on this connection, so the packet is refused instead.
                    throw new DecoderException("Member count out of range: " + size);
                }
                List<Member> members = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    UUID id = UUIDUtil.STREAM_CODEC.decode(buffer);
                    members.add(new Member(id, buffer.readUtf(64), buffer.readBoolean()));
                }
                return new XppMembersPayload(pos, teamId, teamLabel, status, requestId, members);
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Builds a payload, dropping any member past {@link #MAX_MEMBERS} rather than failing the request. */
    public static XppMembersPayload of(BlockPos pos, UUID teamId, String teamLabel, byte status, long requestId,
                                       List<Member> members) {
        List<Member> capped = members.size() <= MAX_MEMBERS
                ? members
                : List.copyOf(members.subList(0, MAX_MEMBERS));
        return new XppMembersPayload(pos, teamId, teamLabel, status, requestId, capped);
    }

    /** Builds and sends the answer to one player. */
    public static void sendTo(ServerPlayer player, BlockPos pos, UUID teamId, String teamLabel, byte status,
                              long requestId, List<Member> members) {
        PacketDistributor.sendToPlayer(player, of(pos, teamId, teamLabel, status, requestId, members));
    }
}
