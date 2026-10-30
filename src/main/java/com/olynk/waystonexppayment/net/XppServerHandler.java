package com.olynk.waystonexppayment.net;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.olynk.waystonexppayment.WaystoneXpPaymentMod;
import com.olynk.waystonexppayment.auth.TeamResolver;
import com.olynk.waystonexppayment.auth.TeamResolvers;
import com.olynk.waystonexppayment.block.entity.XpPaymentTankBlockEntity;
import com.olynk.waystonexppayment.config.XppSettings;
import com.olynk.waystonexppayment.data.TankPoolIndex;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server side of the tank screen.
 *
 * <p>Every request is revalidated here (block entity at that position, owner, target identity) and answered with
 * a fresh state snapshot, so the client is never trusted for access control. Identity is equally untrusted: a
 * client may propose any UUID, but the server accepts one only if it is an online player or a team the team mod
 * knows. Editing requires the tank's owner, a player in the owner's team, or an operator - the client shows the
 * same rule as a read-only screen, and a refused request still gets a snapshot back so the screen corrects
 * itself instead of keeping an optimistic edit.
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
            // Decided before anything else, and answered with a snapshot either way, so a refused client still
            // ends up with the truth on screen instead of whatever it had optimistically drawn. Who may edit is
            // canEditOrOp's rule, the same call the screen makes, so the two sides cannot disagree about it.
            boolean permitted = tank.canEditOrOp(player);
            if (payload.action() == XppAccessPayload.Action.REQUEST_MEMBERS) {
                answerMembers(tank, player, payload, permitted);
                return;
            }
            if (!permitted) {
                deny(tank, player, payload, "not the owner or in the owner's team");
                return;
            }
            apply(tank, player, payload);
            XppStatePayload.sendTo(player, pos, tank, TeamResolvers.get().teamsOf(player));
        });
    }

    /**
     * Answers "is there a pool below any of these waystones?" for the list the client is drawing.
     *
     * <p>Three answers per slot, and the difference matters: a pool, "looked and there is nothing here", or
     * "chunk not resident and nothing recorded". The last one must never be reported as "nothing here" - that
     * is exactly the far-away case this feature exists for, and hiding it would make the button grey again.
     *
     * <p>Nothing is loaded here: the client may ask this per menu, and a menu is not a teleport.
     */
    public static void handleHintQuery(XppTankHintQueryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ServerLevel level = player.server.getLevel(
                    ResourceKey.create(Registries.DIMENSION, payload.dimension()));
            if (level == null) {
                return;
            }
            List<XppTankHintPayload.Hint> hints = new ArrayList<>(payload.tankSlots().size());
            for (BlockPos slot : payload.tankSlots()) {
                hints.add(hintFor(level, slot, player));
            }
            PacketDistributor.sendToPlayer(player, XppTankHintPayload.of(payload.dimension(), hints));
        });
    }

    /**
     * One slot's answer, without loading anything.
     *
     * <p>The viewer is part of the question: a tank that exists but whose access list excludes this player must
     * not have its pool quoted, and the client has no way to work that out on its own. The tri-state exists so
     * "you may not use it" and "nobody can tell yet" stay distinguishable - only the first one may hide the tank.
     */
    private static XppTankHintPayload.Hint hintFor(ServerLevel level, BlockPos slot, ServerPlayer viewer) {
        boolean remoteEnabled = XppSettings.current().remoteDestinationPayments();
        if (level.isLoaded(slot)) {
            if (level.getBlockEntity(slot) instanceof XpPaymentTankBlockEntity tank) {
                return accessAwareHint(slot, tank, viewer);
            }
            if (remoteEnabled) {
                // A resident chunk that has no tank is a fact worth keeping: it is what spares the next query
                // (and a client restart) from having to ask again.
                TankPoolIndex.get(level.getServer())
                        .record(level.dimension().location(), slot, TankPoolIndex.NO_TANK, 0,
                                System.currentTimeMillis());
            }
            return XppTankHintPayload.Hint.none(slot);
        }
        if (!remoteEnabled) {
            // The server will not load this chunk to pay, so it genuinely cannot tell what sits there. Reported
            // as UNKNOWN rather than "no tank": claiming absence would hide a tank that is in fact present.
            return XppTankHintPayload.Hint.unknown(slot);
        }
        TankPoolIndex.Pool recorded = TankPoolIndex.get(level.getServer())
                .get(level.dimension().location(), slot);
        if (recorded == null) {
            return XppTankHintPayload.Hint.unknown(slot);
        }
        // A recorded amount says nothing about who may use it, so the access question stays open here.
        return new XppTankHintPayload.Hint(slot, recorded.poolXp(), recorded.xpPerBucketMilli(),
                XppTankHintPayload.Access.UNKNOWN);
    }

    /** Quotes a resident tank's pool only to a viewer the access list admits. */
    private static XppTankHintPayload.Hint accessAwareHint(BlockPos slot, XpPaymentTankBlockEntity tank,
                                                           ServerPlayer viewer) {
        TeamResolver resolver = TeamResolvers.get();
        boolean allowed = tank.access().allows(viewer, resolver);
        XppTankHintPayload.Access access = HintAccessRule.of(allowed, resolver.available(),
                !tank.access().teams().isEmpty());
        if (access == XppTankHintPayload.Access.DENIED) {
            // The position is reported so the client can stop asking; the amount is not, because this player
            // cannot spend it and quoting it would leak the owner's stock to anyone who right-clicks a waystone.
            return XppTankHintPayload.Hint.denied(slot);
        }
        return new XppTankHintPayload.Hint(slot, tank.poolXp(), tank.xpPerBucketMilli(), access);
    }

    /**
     * Answers a member request, including when the request was refused.
     *
     * <p>The refusal has to send the member packet as well, not just the tank snapshot: the panel shows
     * neither the list nor the status bar's list segment while it waits, so a snapshot alone would leave it
     * waiting for a reply that is never coming.
     */
    private static void answerMembers(XpPaymentTankBlockEntity tank, ServerPlayer player, XppAccessPayload payload,
                                      boolean permitted) {
        UUID teamId = payload.target();
        if (!permitted) {
            log(tank, player, "refused REQUEST_MEMBERS: not the owner or in the owner's team",
                    teamId, payload.label());
            XppMembersPayload.sendTo(player, payload.pos(), teamId, payload.label(),
                    XppMembersStatus.NOT_OWNER, payload.requestId(), List.of());
            XppStatePayload.sendTo(player, payload.pos(), tank, TeamResolvers.get().teamsOf(player));
            return;
        }
        // A request that names no team cannot be looked up; answering "no such team" keeps the panel from
        // hanging on a malformed request, which is the only thing it could usefully do with one anyway.
        TeamResolver.MembersResult result = teamId == null || XppAccessPayload.NO_TARGET.equals(teamId)
                ? TeamResolver.MembersResult.unknownTeam()
                : TeamResolvers.get().membersOf(teamId);
        log(tank, player, "read " + result.members().size() + " team members (status " + result.status() + ")",
                teamId, payload.label());
        List<XppMembersPayload.Member> members = new ArrayList<>(result.members().size());
        for (TeamResolver.MemberInfo member : result.members()) {
            members.add(new XppMembersPayload.Member(member.id(), member.name(), member.online()));
        }
        XppMembersPayload.sendTo(player, payload.pos(), teamId, payload.label(), result.status(),
                payload.requestId(), members);
    }

    /** Refusal path: log, tell the player why, and push the authoritative state back to their screen. */
    private static void deny(XpPaymentTankBlockEntity tank, ServerPlayer player, XppAccessPayload payload,
                             String reason) {
        log(tank, player, "refused " + payload.action() + ": " + reason, payload.target(), payload.label());
        player.displayClientMessage(Component.translatable("access.no_permission"), false);
        XppStatePayload.sendTo(player, payload.pos(), tank, TeamResolvers.get().teamsOf(player));
    }

    private static void apply(XpPaymentTankBlockEntity tank, ServerPlayer player, XppAccessPayload payload) {
        // The owner check protects every action, REFRESH included: a snapshot of a tank you may not edit is
        // still the owner's business, and one gate in one place cannot be forgotten in a later branch.
        boolean changed = switch (payload.action()) {
            case SET_MODE -> setMode(tank, player, payload);
            case ADD_PLAYER -> addPlayer(tank, player, payload);
            case REMOVE_PLAYER -> removePlayer(tank, player, payload);
            case ADD_TEAM -> addTeam(tank, player, payload);
            case REMOVE_TEAM -> removeTeam(tank, player, payload);
            case REFRESH -> {
                // nothing to change; the caller only wants the fresh snapshot below
                yield false;
            }
            case REQUEST_MEMBERS -> {
                // Unreachable: handle() answers member requests before this switch, because the answer is a
                // different payload rather than a change to the tank. Listed so the enum stays exhaustively
                // covered - a new action must not be able to slip past this switch unnoticed.
                yield false;
            }
        };
        if (!changed) {
            return;
        }
        // The first successful edit claims an unclaimed tank, so a tank placed before this model existed (or by
        // something that is not a player) ends up with an owner the moment somebody configures it.
        if (tank.ownerId() == null) {
            String name = player.getGameProfile().getName();
            tank.setOwner(player.getUUID(), name);
            WaystoneXpPaymentMod.LOGGER.info("Tank {} claimed by {} [uuid={}]",
                    tank.getBlockPos().toShortString(), name, player.getUUID());
            player.displayClientMessage(Component.translatable("access.claimed", name), false);
        }
        tank.setChanged();
    }

    /**
     * Applies the requested mode; true only when it actually changed, so a no-op does not claim a tank.
     *
     * <p>The mode arrives as its own field. It used to be inferred from {@code payload.label()} being the string
     * "blacklist", which made a permission rule depend on display text; a missing field is now refused instead of
     * silently defaulting to whitelist.
     */
    private static boolean setMode(XpPaymentTankBlockEntity tank, ServerPlayer player, XppAccessPayload payload) {
        XppAccessPayload.Mode mode = payload.mode();
        if (mode == null) {
            log(tank, player, "refused SET_MODE without a mode", payload.target(), payload.label());
            return false;
        }
        boolean whitelist = mode == XppAccessPayload.Mode.WHITELIST;
        if (tank.access().isWhitelist() == whitelist) {
            return false;
        }
        tank.access().setWhitelist(whitelist);
        return true;
    }

    /**
     * Only currently online players may be added.     *
     * <p>The UUID comes from the client, so it is checked against the server's own list rather than trusted: any
     * id that is not an online player here would be an unverifiable identity stored forever in the world save.
     */
    private static boolean addPlayer(XpPaymentTankBlockEntity tank, ServerPlayer player, XppAccessPayload payload) {
        UUID target = payload.target();
        if (target == null || XppAccessPayload.NO_TARGET.equals(target)
                || !onlinePlayers(player).contains(target)) {
            log(tank, player, "refused ADD_PLAYER for a UUID that is not online", target, payload.label());
            return false;
        }
        boolean added = tank.access().addPlayer(target);
        log(tank, player, added ? "added player" : "player already listed", target, payload.label());
        return added;
    }

    /** Only a team the team mod actually knows may be added. Membership is not required (see the javadoc above). */
    private static boolean addTeam(XpPaymentTankBlockEntity tank, ServerPlayer player, XppAccessPayload payload) {
        UUID target = payload.target();
        if (target == null || XppAccessPayload.NO_TARGET.equals(target)
                || !allTeamIds().contains(target)) {
            log(tank, player, "refused ADD_TEAM for an unknown team", target, payload.label());
            return false;
        }
        boolean added = tank.access().addTeam(target);
        log(tank, player, added ? "added team" : "team already listed", target, payload.label());
        return added;
    }

    private static Set<UUID> onlinePlayers(ServerPlayer viewer) {
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer other : viewer.server.getPlayerList().getPlayers()) {
            online.add(other.getUUID());
        }
        return online;
    }

    private static Set<UUID> allTeamIds() {
        Set<UUID> teams = new HashSet<>();
        for (TeamResolver.TeamInfo team : TeamResolvers.get().allTeams()) {
            teams.add(team.id());
        }
        return teams;
    }

    private static boolean removePlayer(XpPaymentTankBlockEntity tank, ServerPlayer player, XppAccessPayload payload) {
        UUID target = payload.target();
        if (target == null || XppAccessPayload.NO_TARGET.equals(target)) {
            return false;
        }
        boolean removedById = tank.access().removePlayer(target);
        // A world saved before the UUID model holds this identity as a name only, and the client cannot know the
        // stored name unless the entry had no UUID at all - so the removal has to try the label as well.
        boolean removedByName = tank.access().removePlayer(payload.label());
        log(tank, player, removedById || removedByName ? "removed player" : "no player entry matched",
                target, payload.label());
        return removedById || removedByName;
    }

    private static boolean removeTeam(XpPaymentTankBlockEntity tank, ServerPlayer player, XppAccessPayload payload) {
        UUID target = payload.target();
        String label = payload.label();
        boolean removedById = target != null && !XppAccessPayload.NO_TARGET.equals(target)
                && tank.access().removeTeam(target);
        boolean removedByName = !label.isBlank() && tank.access().removeTeam(label);
        log(tank, player, removedById || removedByName ? "removed team" : "no team entry matched", target, label);
        return removedById || removedByName;
    }

    private static void log(XpPaymentTankBlockEntity tank, ServerPlayer player, String what, UUID target,
                            String label) {
        WaystoneXpPaymentMod.LOGGER.info("Tank {} access change by {}: {} [uuid={}, label='{}']",
                tank.getBlockPos().toShortString(), player.getGameProfile().getName(), what,
                target == null ? "none" : target, label);
    }
}
