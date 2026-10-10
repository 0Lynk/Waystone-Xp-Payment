package com.olynk.waystonexppayment.auth;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.olynk.waystonexppayment.WaystoneXpPaymentMod;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * FTB Teams resolver, driven entirely through reflection.
 *
 * <p>Reflection rather than a compile-time dependency on purpose: it keeps the build free of FTB's own maven
 * repository (which is not needed for anything else), and it turns "FTB Teams changed its API" into a
 * degraded-but-alive tank instead of a {@link NoClassDefFoundError} at mod construction. {@link TeamResolvers}
 * still guards the whole thing behind a mod-loaded check and a {@link Throwable} catch.
 *
 * <p>Reflected surface (FTB Teams 2101.x):
 * {@code FTBTeamsAPI.api()} -> {@code isManagerLoaded()} / {@code getManager()};
 * {@code TeamManager.getTeamForPlayerID(UUID)} -> {@code Optional<Team>}, {@code getTeams()};
 * {@code Team.getId()}, {@code Team.getName()}, {@code Team.isValid()}.
 */
final class FtbTeamsResolver implements TeamResolver {

    private static final String API_CLASS = "dev.ftb.mods.ftbteams.api.FTBTeamsAPI";
    private static final String TEAM_CLASS = "dev.ftb.mods.ftbteams.api.Team";

    static final FtbTeamsResolver INSTANCE = new FtbTeamsResolver();

    private final Object api;
    private final Method isManagerLoaded;
    private final Method getManager;
    private final Method getTeamForPlayerId;
    private final Method getTeams;
    private final Method teamGetId;
    private final Method teamGetName;
    private final Method teamIsValid;

    private FtbTeamsResolver() {
        try {
            Class<?> apiClass = Class.forName(API_CLASS);
            Class<?> teamClass = Class.forName(TEAM_CLASS);
            this.api = apiClass.getMethod("api").invoke(null);
            // isManagerLoaded()/getManager() live on FTBTeamsAPI$API (what api() returns), NOT on FTBTeamsAPI
            // itself - addressing the outer class here threw NoSuchMethodException and silently disabled team
            // support on a live client.
            Class<?> apiInterface = Class.forName(API_CLASS + "$API");
            this.isManagerLoaded = apiInterface.getMethod("isManagerLoaded");
            this.getManager = apiInterface.getMethod("getManager");
            Class<?> managerClass = Class.forName("dev.ftb.mods.ftbteams.api.TeamManager");
            this.getTeamForPlayerId = managerClass.getMethod("getTeamForPlayerID", UUID.class);
            this.getTeams = managerClass.getMethod("getTeams");
            this.teamGetId = teamClass.getMethod("getId");
            this.teamGetName = teamClass.getMethod("getName");
            this.teamIsValid = teamClass.getMethod("isValid");
        } catch (ReflectiveOperationException e) {
            // TeamResolvers catches this and falls back to "no teams"; keep the cause for the log.
            throw new IllegalStateException("FTB Teams API is not usable: " + e, e);
        }
    }

    @Override
    public List<TeamInfo> teamsOf(Player player) {
        try {
            if (!managerLoaded()) {
                return List.of();
            }
            Optional<?> team = teamForPlayer(player.getUUID());
            if (team.isEmpty()) {
                return List.of();
            }
            Object value = team.get();
            if (!(Boolean) teamIsValid.invoke(value)) {
                return List.of();
            }
            return List.of(toInfo(value));
        } catch (ReflectiveOperationException | RuntimeException e) {
            WaystoneXpPaymentMod.LOGGER.warn("Could not read the player's FTB team, ignoring it", e);
            return List.of();
        }
    }

    @Override
    public List<TeamInfo> allTeams() {
        try {
            if (!managerLoaded()) {
                return List.of();
            }
            Collection<?> teams = (Collection<?>) getTeams.invoke(getManager.invoke(api));
            List<TeamInfo> result = new ArrayList<>(teams.size());
            for (Object team : teams) {
                if ((Boolean) teamIsValid.invoke(team)) {
                    result.add(toInfo(team));
                }
            }
            return result;
        } catch (ReflectiveOperationException | RuntimeException e) {
            WaystoneXpPaymentMod.LOGGER.warn("Could not list FTB teams", e);
            return List.of();
        }
    }

    private boolean managerLoaded() throws ReflectiveOperationException {
        return (Boolean) isManagerLoaded.invoke(api);
    }

    private Optional<?> teamForPlayer(UUID playerId) throws ReflectiveOperationException {
        return (Optional<?>) getTeamForPlayerId.invoke(getManager.invoke(api), playerId);
    }

    private TeamInfo toInfo(Object team) throws ReflectiveOperationException {
        return new TeamInfo((UUID) teamGetId.invoke(team), ((Component) teamGetName.invoke(team)).getString());
    }
}
