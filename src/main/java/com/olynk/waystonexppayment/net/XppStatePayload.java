package com.olynk.waystonexppayment.net;

import java.util.ArrayList;
import java.util.List;

import com.olynk.waystonexppayment.WaystoneXpPaymentMod;
import com.olynk.waystonexppayment.auth.AccessList;
import com.olynk.waystonexppayment.auth.TeamResolver;
import com.olynk.waystonexppayment.block.entity.XpPaymentTankBlockEntity;
import com.olynk.waystonexppayment.config.XppSettings;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server -> client snapshot of everything the tank screen shows. */
public record XppStatePayload(BlockPos pos, FluidStack fluid, int capacity, int poolXp, int mbPerXp,
                              boolean whitelist, List<String> players, List<String> teams,
                              List<String> ownTeams) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<XppStatePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    WaystoneXpPaymentMod.MOD_ID, "tank_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, XppStatePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.pos());
                FluidStack.STREAM_CODEC.encode(buffer, payload.fluid());
                buffer.writeVarInt(payload.capacity());
                buffer.writeVarInt(payload.poolXp());
                buffer.writeVarInt(payload.mbPerXp());
                buffer.writeBoolean(payload.whitelist());
                writeStrings(buffer, payload.players());
                writeStrings(buffer, payload.teams());
                writeStrings(buffer, payload.ownTeams());
            },
            buffer -> new XppStatePayload(
                    buffer.readBlockPos(),
                    FluidStack.STREAM_CODEC.decode(buffer),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readBoolean(),
                    readStrings(buffer),
                    readStrings(buffer),
                    readStrings(buffer)));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Builds and sends the current state of the tank to one player. */
    public static void sendTo(ServerPlayer player, BlockPos pos, XpPaymentTankBlockEntity tank,
                              List<TeamResolver.TeamInfo> ownTeams) {
        AccessList access = tank.access();
        List<String> players = new ArrayList<>();
        for (AccessList.PlayerEntry entry : access.players()) {
            players.add(entry.name() != null ? entry.name() : String.valueOf(entry.uuid()));
        }
        List<String> teams = new ArrayList<>();
        for (AccessList.TeamEntry entry : access.teams()) {
            teams.add(entry.name() != null ? entry.name() : String.valueOf(entry.id()));
        }
        List<String> teamNames = new ArrayList<>();
        for (TeamResolver.TeamInfo team : ownTeams) {
            teamNames.add(team.name());
        }
        PacketDistributor.sendToPlayer(player, new XppStatePayload(pos, tank.getTank().getFluid(),
                tank.getTank().getCapacity(), tank.poolXp(), tank.mbPerXp(), access.isWhitelist(),
                players, teams, teamNames));
    }

    /** Tank capacity exposed for the screen's "x / y mB" line; falls back to the configured value. */
    public int capacityOrDefault() {
        return capacity > 0 ? capacity : XppSettings.current().capacityMb();
    }

    private static void writeStrings(FriendlyByteBuf buffer, List<String> values) {
        buffer.writeVarInt(values.size());
        for (String value : values) {
            buffer.writeUtf(value, 64);
        }
    }

    private static List<String> readStrings(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<String> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(buffer.readUtf(64));
        }
        return values;
    }
}
