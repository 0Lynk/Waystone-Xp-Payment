package com.olynk.waystonexppayment.command;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.olynk.waystonexppayment.auth.AccessList;
import com.olynk.waystonexppayment.auth.TeamResolver;
import com.olynk.waystonexppayment.auth.TeamResolvers;
import com.olynk.waystonexppayment.block.entity.XpPaymentTankBlockEntity;
import com.olynk.waystonexppayment.fluid.FluidRates;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * {@code /xpp ...} - inspect and configure the payment tank the caller is looking at.
 *
 * <p>The GUI exposes the same operations; the command exists so the feature is testable and automatable
 * (smoke tests, RCON, datapacks) without a client.
 */
public final class XppCommands {

    private static final SimpleCommandExceptionType ERROR_NOT_LOOKING_AT_TANK =
            new SimpleCommandExceptionType(Component.translatable("commands.waystone_xp_payment.not_looking_at_tank"));
    private static final SimpleCommandExceptionType ERROR_NO_TEAM_MOD =
            new SimpleCommandExceptionType(Component.translatable("commands.waystone_xp_payment.no_team_mod"));
    private static final SimpleCommandExceptionType ERROR_UNKNOWN_TEAM =
            new SimpleCommandExceptionType(Component.translatable("commands.waystone_xp_payment.unknown_team"));
    private static final SimpleCommandExceptionType ERROR_UNKNOWN_PLAYER =
            new SimpleCommandExceptionType(Component.translatable("commands.waystone_xp_payment.unknown_player"));

    private XppCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("waystonexppayment")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("info")
                        .executes(XppCommands::info))
                .then(Commands.literal("access")
                        .then(Commands.literal("mode")
                                .then(Commands.literal("whitelist").executes(ctx -> mode(ctx, true)))
                                .then(Commands.literal("blacklist").executes(ctx -> mode(ctx, false))))
                        .then(Commands.literal("addplayer")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> addPlayer(ctx, StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("removeplayer")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> removePlayer(ctx, StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("addteam")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> addTeam(ctx, StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("removeteam")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> removeTeam(ctx, StringArgumentType.getString(ctx, "name")))))));
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        XpPaymentTankBlockEntity tank = tank(ctx);
        AccessList access = tank.access();
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.waystone_xp_payment.info.tank",
                tank.getBlockPos().toShortString(),
                tank.getTank().getFluid().getHoverName(),
                tank.getTank().getFluidAmount(),
                tank.getTank().getCapacity(),
                tank.poolXp(),
                FluidRates.describe(tank.getTank().getFluid()),
                modeName(access.isWhitelist()),
                access.players().size(),
                access.teams().size()), false);
        // Legacy entries are counted separately because they are the one thing a reader of this output cannot
        // see anywhere else: they have no UUID to look up, and they are the reason a tank may pay for somebody
        // who is not on the list above.
        int legacy = access.legacyPlayers().size() + access.legacyTeams().size();
        if (legacy > 0) {
            ctx.getSource().sendSuccess(
                    () -> Component.translatable("commands.waystone_xp_payment.info.legacy", legacy), false);
        }
        // Identities only, never names: a UUID is what the list matches on, and a name would go stale.
        int index = 0;
        for (UUID id : access.players()) {
            int line = index++;
            ctx.getSource().sendSuccess(
                    () -> Component.translatable("commands.waystone_xp_payment.info.player", line, id.toString()), false);
        }
        index = 0;
        for (UUID id : access.teams()) {
            int line = index++;
            ctx.getSource().sendSuccess(
                    () -> Component.translatable("commands.waystone_xp_payment.info.team", line, id.toString()), false);
        }
        return 1;
    }

    /** The two access modes are already named for the GUI; a second pair of words would only drift from it. */
    private static Component modeName(boolean whitelist) {
        return Component.translatable(whitelist ? "access.mode.whitelist" : "access.mode.blacklist");
    }

    private static int mode(CommandContext<CommandSourceStack> ctx, boolean whitelist) throws CommandSyntaxException {
        XpPaymentTankBlockEntity tank = tank(ctx);
        tank.access().setWhitelist(whitelist);
        tank.setChanged();
        ctx.getSource().sendSuccess(
                () -> Component.translatable("commands.waystone_xp_payment.mode.changed", modeName(whitelist)), true);
        return 1;
    }

    /**
     * Adds a player by name, resolving the name to the game-profile UUID.
     *
     * <p>Offline players are resolved from the server's profile cache on purpose: an operator setting up a tank
     * for somebody who is not online is the normal case, and refusing that would make the command useless for
     * its main purpose. Only a name nobody on this server has ever used is an error.
     */
    private static int addPlayer(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        XpPaymentTankBlockEntity tank = tank(ctx);
        GameProfile profile = resolveProfile(ctx.getSource().getServer(), name);
        if (profile == null) {
            throw ERROR_UNKNOWN_PLAYER.create();
        }
        boolean added = tank.access().addPlayer(profile.getId());
        tank.setChanged();
        ctx.getSource().sendSuccess(() -> Component.translatable(added
                ? "commands.waystone_xp_payment.add.player"
                : "commands.waystone_xp_payment.add.player.already",
                profile.getName(), profile.getId().toString()), true);
        return added ? 1 : 0;
    }

    private static int removePlayer(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        XpPaymentTankBlockEntity tank = tank(ctx);
        AccessList access = tank.access();
        UUID id = parseUuid(name);
        if (id == null) {
            // A name is resolved first so removing an entry that carries a UUID works by name too - the operator
            // reads names in the GUI, not UUIDs.
            GameProfile profile = resolveProfile(ctx.getSource().getServer(), name);
            id = profile != null ? profile.getId() : null;
        }
        boolean removed = id != null && access.removePlayer(id);
        if (!removed) {
            // Falls through to a bare name, which is the only way to reach a legacy entry.
            removed = access.removePlayer(name);
        }
        tank.setChanged();
        boolean result = removed;
        ctx.getSource().sendSuccess(() -> Component.translatable(result
                ? "commands.waystone_xp_payment.remove.player"
                : "commands.waystone_xp_payment.remove.player.none", name), true);
        return result ? 1 : 0;
    }

    private static int addTeam(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        XpPaymentTankBlockEntity tank = tank(ctx);
        TeamResolver resolver = TeamResolvers.get();
        List<TeamResolver.TeamInfo> teams = resolver.allTeams();
        if (teams.isEmpty()) {
            throw ERROR_NO_TEAM_MOD.create();
        }
        Optional<TeamResolver.TeamInfo> match = teams.stream()
                .filter(team -> team.name().equalsIgnoreCase(name))
                .findFirst();
        if (match.isEmpty()) {
            throw ERROR_UNKNOWN_TEAM.create();
        }
        TeamResolver.TeamInfo team = match.get();
        boolean added = tank.access().addTeam(team.id());
        tank.setChanged();
        ctx.getSource().sendSuccess(() -> Component.translatable(added
                ? "commands.waystone_xp_payment.add.team"
                : "commands.waystone_xp_payment.add.team.already",
                team.name(), team.id().toString()), true);
        return added ? 1 : 0;
    }

    private static int removeTeam(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        XpPaymentTankBlockEntity tank = tank(ctx);
        AccessList access = tank.access();
        UUID id = parseUuid(name);
        boolean removed = id != null
                ? access.removeTeam(id)
                : access.removeTeam(name);
        if (!removed && id == null) {
            // A team can also be named by the operator, so the name is resolved through the mod and the removal
            // retried by id - otherwise "removeteam My Team" would fail for every team added through the GUI.
            TeamResolver.TeamInfo match = findTeamByName(TeamResolvers.get(), name);
            removed = match != null && access.removeTeam(match.id());
        }
        tank.setChanged();
        boolean result = removed;
        ctx.getSource().sendSuccess(() -> Component.translatable(result
                ? "commands.waystone_xp_payment.remove.team"
                : "commands.waystone_xp_payment.remove.team.none", name), true);
        return result ? 1 : 0;
    }

    /** Name to UUID lookup: the online list first, then the profile cache the server keeps for offline players. */
    private static GameProfile resolveProfile(MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) {
            return online.getGameProfile();
        }
        // The profile cache lives on the server, not on the player list - and it is absent on a server that has
        // name lookups disabled, in which case only online players can be resolved.
        if (server.getProfileCache() == null) {
            return null;
        }
        return server.getProfileCache().get(name).orElse(null);
    }

    private static TeamResolver.TeamInfo findTeamByName(TeamResolver resolver, String name) {
        for (TeamResolver.TeamInfo team : resolver.allTeams()) {
            if (team.name().equalsIgnoreCase(name)) {
                return team;
            }
        }
        return null;
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            // Not a UUID - the caller meant a name, which is a supported way to address a legacy entry.
            return null;
        }
    }

    private static XpPaymentTankBlockEntity tank(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        HitResult hit = player.pick(10.0D, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            throw ERROR_NOT_LOOKING_AT_TANK.create();
        }
        if (player.serverLevel().getBlockEntity(blockHit.getBlockPos()) instanceof XpPaymentTankBlockEntity tank) {
            return tank;
        }
        throw ERROR_NOT_LOOKING_AT_TANK.create();
    }
}
