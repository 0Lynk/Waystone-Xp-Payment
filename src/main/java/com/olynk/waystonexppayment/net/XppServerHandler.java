package com.olynk.waystonexppayment.net;

import java.util.List;
import java.util.Optional;

import com.olynk.waystonexppayment.auth.TeamResolver;
import com.olynk.waystonexppayment.auth.TeamResolvers;
import com.olynk.waystonexppayment.block.entity.XpPaymentTankBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server side of the tank screen.
 *
 * <p>Every request is revalidated here (block entity at that position, op level) and answered with a fresh
 * state snapshot, so the client is never trusted for access control.
 */
public final class XppServerHandler {

    private XppServerHandler() {
    }

    public static void handle(XppAccessPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            BlockPos pos = payload.pos();
            if (!(player.serverLevel().getBlockEntity(pos) instanceof XpPaymentTankBlockEntity tank)) {
                return;
            }
            if (!player.hasPermissions(2)) {
                player.displayClientMessage(
                        Component.translatable("commands.waystone_xp_payment.no_permission"), false);
                return;
            }
            apply(tank, payload);
            XppStatePayload.sendTo(player, pos, tank, TeamResolvers.get().teamsOf(player));
        });
    }

    private static void apply(XpPaymentTankBlockEntity tank, XppAccessPayload payload) {
        switch (payload.action()) {
            case SET_MODE -> tank.access().setWhitelist(!"blacklist".equalsIgnoreCase(payload.value()));
            case ADD_PLAYER -> tank.access().addPlayer(payload.value(), null);
            case REMOVE_PLAYER -> tank.access().removePlayer(payload.value());
            case ADD_TEAM -> findTeam(payload.value())
                    .ifPresent(team -> tank.access().addTeam(team.id(), team.name()));
            case REMOVE_TEAM -> tank.access().removeTeam(payload.value());
            case REFRESH -> {
                // nothing to change; the caller only wants the fresh snapshot below
            }
        }
        tank.setChanged();
    }

    private static Optional<TeamResolver.TeamInfo> findTeam(String name) {
        List<TeamResolver.TeamInfo> teams = TeamResolvers.get().allTeams();
        return teams.stream().filter(team -> team.name().equalsIgnoreCase(name)).findFirst();
    }
}
