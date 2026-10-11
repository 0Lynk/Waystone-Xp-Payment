package com.olynk.waystonexppayment.net;

import java.util.UUID;

import com.olynk.waystonexppayment.WaystoneXpPaymentMod;
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
 * state snapshot, so the client is never trusted for access control. Identity is equally untrusted: the client
 * may only add itself as a player and only its own teams, whatever UUID it puts on the wire.
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
            apply(tank, player, payload);
            XppStatePayload.sendTo(player, pos, tank, TeamResolvers.get().teamsOf(player));
        });
    }

    private static void apply(XpPaymentTankBlockEntity tank, ServerPlayer player, XppAccessPayload payload) {
        switch (payload.action()) {
            case SET_MODE -> tank.access().setWhitelist(!"blacklist".equalsIgnoreCase(payload.label()));
            case ADD_PLAYER -> addSelf(tank, player, payload);
            case REMOVE_PLAYER -> removePlayer(tank, player, payload);
            case ADD_TEAM -> addOwnTeam(tank, player, payload);
            case REMOVE_TEAM -> removeTeam(tank, player, payload);
            case REFRESH -> {
                // nothing to change; the caller only wants the fresh snapshot below
            }
        }
        tank.setChanged();
    }

    /**
     * Only the sender's own game-profile UUID may be added by a client. The label is deliberately ignored: it
     * is free text from an untrusted side and must never become an identity.
     */
    private static void addSelf(XpPaymentTankBlockEntity tank, ServerPlayer player, XppAccessPayload payload) {
        UUID target = payload.target();
        if (!player.getUUID().equals(target)) {
            log(tank, player, "refused ADD_PLAYER for a UUID that is not the sender's", target, payload.label());
            return;
        }
        boolean added = tank.access().addPlayer(target);
        log(tank, player, added ? "added player" : "player already listed", target, payload.label());
    }

    /** Only a team the sender is currently in may be added. */
    private static void addOwnTeam(XpPaymentTankBlockEntity tank, ServerPlayer player, XppAccessPayload payload) {
        UUID target = payload.target();
        TeamResolver.TeamInfo ownTeam = null;
        for (TeamResolver.TeamInfo team : TeamResolvers.get().teamsOf(player)) {
            if (team.id().equals(target)) {
                ownTeam = team;
                break;
            }
        }
        if (ownTeam == null) {
            log(tank, player, "refused ADD_TEAM for a team the sender is not in", target, payload.label());
            return;
        }
        boolean added = tank.access().addTeam(target);
        log(tank, player, added ? "added team " + ownTeam.name() : "team already listed", target, payload.label());
    }

    private static void removePlayer(XpPaymentTankBlockEntity tank, ServerPlayer player, XppAccessPayload payload) {
        UUID target = payload.target();
        if (target == null || XppAccessPayload.NO_TARGET.equals(target)) {
            return;
        }
        boolean removedById = tank.access().removePlayer(target);
        // A world saved before the UUID model holds this identity as a name only, and the client cannot know the
        // stored name unless the entry had no UUID at all - so the removal has to try the label as well.
        boolean removedByName = tank.access().removePlayer(payload.label());
        log(tank, player, removedById || removedByName ? "removed player" : "no player entry matched",
                target, payload.label());
    }

    private static void removeTeam(XpPaymentTankBlockEntity tank, ServerPlayer player, XppAccessPayload payload) {
        UUID target = payload.target();
        String label = payload.label();
        boolean removedById = target != null && !XppAccessPayload.NO_TARGET.equals(target)
                && tank.access().removeTeam(target);
        boolean removedByName = !label.isBlank() && tank.access().removeTeam(label);
        log(tank, player, removedById || removedByName ? "removed team" : "no team entry matched", target, label);
    }

    private static void log(XpPaymentTankBlockEntity tank, ServerPlayer player, String what, UUID target,
                            String label) {
        WaystoneXpPaymentMod.LOGGER.info("Tank {} access change by {}: {} [uuid={}, label='{}']",
                tank.getBlockPos().toShortString(), player.getGameProfile().getName(), what,
                target == null ? "none" : target, label);
    }
}
