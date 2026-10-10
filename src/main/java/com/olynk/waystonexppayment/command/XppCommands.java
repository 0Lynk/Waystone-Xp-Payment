package com.olynk.waystonexppayment.command;

import java.util.List;
import java.util.Optional;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.olynk.waystonexppayment.auth.AccessList;
import com.olynk.waystonexppayment.auth.TeamResolver;
import com.olynk.waystonexppayment.auth.TeamResolvers;
import com.olynk.waystonexppayment.block.entity.XpPaymentTankBlockEntity;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
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
        ctx.getSource().sendSuccess(() -> Component.literal("Waystone XP Payment tank at "
                + tank.getBlockPos().toShortString()
                + " | fluid=" + tank.getTank().getFluid().getHoverName().getString()
                + " " + tank.getTank().getFluidAmount() + "/" + tank.getTank().getCapacity() + " mB"
                + " | value=" + tank.poolXp() + " XP"
                + " | mode=" + (access.isWhitelist() ? "whitelist" : "blacklist")
                + " | players=" + access.players().size() + " teams=" + access.teams().size()), false);
        return 1;
    }

    private static int mode(CommandContext<CommandSourceStack> ctx, boolean whitelist) throws CommandSyntaxException {
        XpPaymentTankBlockEntity tank = tank(ctx);
        tank.access().setWhitelist(whitelist);
        tank.setChanged();
        ctx.getSource().sendSuccess(() -> Component.literal("Tank access mode is now "
                + (whitelist ? "whitelist" : "blacklist")), true);
        return 1;
    }

    private static int addPlayer(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        XpPaymentTankBlockEntity tank = tank(ctx);
        boolean added = tank.access().addPlayer(name, null);
        tank.setChanged();
        ctx.getSource().sendSuccess(() -> Component.literal(added
                ? "Added player '" + name + "' to the tank access list"
                : "Player '" + name + "' was already on the tank access list"), true);
        return added ? 1 : 0;
    }

    private static int removePlayer(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        XpPaymentTankBlockEntity tank = tank(ctx);
        boolean removed = tank.access().removePlayer(name);
        tank.setChanged();
        ctx.getSource().sendSuccess(() -> Component.literal(removed
                ? "Removed '" + name + "' from the tank access list"
                : "No entry matched '" + name + "'"), true);
        return removed ? 1 : 0;
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
        boolean added = tank.access().addTeam(team.id(), team.name());
        tank.setChanged();
        ctx.getSource().sendSuccess(() -> Component.literal(added
                ? "Added team '" + team.name() + "' to the tank access list"
                : "Team '" + team.name() + "' was already on the tank access list"), true);
        return added ? 1 : 0;
    }

    private static int removeTeam(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        XpPaymentTankBlockEntity tank = tank(ctx);
        boolean removed = tank.access().removeTeam(name);
        tank.setChanged();
        ctx.getSource().sendSuccess(() -> Component.literal(removed
                ? "Removed team '" + name + "' from the tank access list"
                : "No team entry matched '" + name + "'"), true);
        return removed ? 1 : 0;
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
