package com.olynk.waystonexppayment.auth;

import java.util.ArrayList;
import java.util.List;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.world.entity.player.Player;

/**
 * FTB Teams resolver.
 *
 * <p>Only ever class-loaded when FTB Teams is present: {@link TeamResolvers} guards the reference behind a
 * mod-loaded check and a {@link Throwable} catch, so a missing or incompatible FTB Teams degrades to
 * "no teams" instead of crashing the game.
 */
final class FtbTeamsResolver implements TeamResolver {

    static final FtbTeamsResolver INSTANCE = new FtbTeamsResolver();

    private FtbTeamsResolver() {
    }

    @Override
    public List<TeamInfo> teamsOf(Player player) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return List.of();
        }
        return FTBTeamsAPI.api().getManager()
                .getTeamForPlayerID(player.getUUID())
                .filter(Team::isValid)
                .map(team -> List.of(toInfo(team)))
                .orElse(List.of());
    }

    @Override
    public List<TeamInfo> allTeams() {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return List.of();
        }
        List<TeamInfo> teams = new ArrayList<>();
        for (Team team : FTBTeamsAPI.api().getManager().getTeams()) {
            if (team.isValid()) {
                teams.add(toInfo(team));
            }
        }
        return teams;
    }

    private static TeamInfo toInfo(Team team) {
        return new TeamInfo(team.getId(), team.getName().getString());
    }
}
