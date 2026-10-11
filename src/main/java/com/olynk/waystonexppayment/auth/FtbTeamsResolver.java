package com.olynk.waystonexppayment.auth;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.olynk.waystonexppayment.WaystoneXpPaymentMod;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * FTB Teams resolver, driven entirely through reflection.
 *
 * <p>Reflection rather than a compile-time dependency on purpose: it keeps the build free of FTB's own maven
 * repository (which is not needed for anything else), and it turns "FTB Teams changed its API" into a
 * degraded-but-alive tank instead of a {@link NoClassDefFoundError} at mod construction. {@link TeamResolvers}
 * still guards the whole thing behind a mod-loaded check and a {@link Throwable} catch.
 *
 * <p>Reflected surface (FTB Teams 2101.x), all confirmed with {@code javap} against
 * {@code ftb-teams-neoforge-2101.1.11.jar}:
 * {@code FTBTeamsAPI.api()} -> {@code isManagerLoaded()} / {@code getManager()};
 * {@code TeamManager.getTeamForPlayerID(UUID)} / {@code getTeamByID(UUID)} -> {@code Optional<Team>},
 * {@code getTeams()};
 * {@code Team.getId()}, {@code Team.getName()} -> {@code Component}, {@code Team.isValid()},
 * {@code Team.getMembers()} -> {@code Set<UUID>}, {@code Team.getOnlineMembers()} -> {@code Collection<ServerPlayer>}.
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
    private final Method getTeamById;
    private final Method teamGetMembers;
    private final Method teamGetOnlineMembers;

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
            this.getTeamById = managerClass.getMethod("getTeamByID", UUID.class);
            this.teamGetId = teamClass.getMethod("getId");
            this.teamGetName = teamClass.getMethod("getName");
            this.teamIsValid = teamClass.getMethod("isValid");
            this.teamGetMembers = teamClass.getMethod("getMembers");
            this.teamGetOnlineMembers = teamClass.getMethod("getOnlineMembers");
        } catch (ReflectiveOperationException e) {
            // TeamResolvers catches this and falls back to "no teams"; keep the cause for the log.
            throw new IllegalStateException("FTB Teams API is not usable: " + e, e);
        }
    }

    @Override
    public List<TeamInfo> teamsOf(Player player) {
        return teamsOf(player.getUUID());
    }

    /** Same lookup as {@link #teamsOf(Player)}, by id: the tank owner check has no {@link Player} to hand. */
    @Override
    public List<TeamInfo> teamsOf(UUID playerId) {
        try {
            if (!managerLoaded()) {
                return List.of();
            }
            Optional<?> team = teamForPlayer(playerId);
            if (team.isEmpty()) {
                return List.of();
            }
            Object value = team.get();
            if (!(Boolean) teamIsValid.invoke(value)) {
                return List.of();
            }
            return List.of(toInfo(value));
        } catch (ReflectiveOperationException | RuntimeException e) {
            WaystoneXpPaymentMod.LOGGER.warn("Could not read the FTB team of player {}, ignoring it", playerId, e);
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

    @Override
    public MembersResult membersOf(UUID teamId) {
        try {
            if (!managerLoaded()) {
                return MembersResult.noTeamMod();
            }
            Optional<?> team = teamById(teamId);
            if (team.isEmpty() || !(Boolean) teamIsValid.invoke(team.get())) {
                // A team that was dissolved while the panel was open resolves to nothing: the caller has to
                // say "no such team" rather than "no members", which is what an empty list would look like.
                return MembersResult.unknownTeam();
            }
            Object value = team.get();
            Map<UUID, String> onlineNames = onlineMemberNames(value);
            Collection<?> ids = (Collection<?>) teamGetMembers.invoke(value);
            List<MemberInfo> members = new ArrayList<>(ids.size());
            for (Object id : ids) {
                UUID memberId = (UUID) id;
                // The online set is the only place a readable name can come from directly; everything else
                // has to be looked up, and a member the server has never seen falls back to their id.
                String online = onlineNames.get(memberId);
                members.add(new MemberInfo(memberId, online != null ? online : offlineName(memberId),
                        online != null));
            }
            // getMembers() is a Set, whose iteration order is not part of its contract: sorting keeps the rows
            // from reshuffling between two identical requests.
            members.sort(Comparator.comparing(MemberInfo::name, String.CASE_INSENSITIVE_ORDER));
            return MembersResult.of(members);
        } catch (ReflectiveOperationException | RuntimeException e) {
            WaystoneXpPaymentMod.LOGGER.warn("Could not read the members of FTB team {}", teamId, e);
            return MembersResult.noTeamMod();
        }
    }

    private Map<UUID, String> onlineMemberNames(Object team) throws ReflectiveOperationException {
        Map<UUID, String> names = new HashMap<>();
        Collection<?> online = (Collection<?>) teamGetOnlineMembers.invoke(team);
        for (Object member : online) {
            if (member instanceof ServerPlayer player) {
                names.put(player.getUUID(), player.getGameProfile().getName());
            }
        }
        return names;
    }

    /**
     * Best name for a member who is not online.
     *
     * <p>FTB stores ids, not names, and an offline member has no {@link ServerPlayer} to read one from; the
     * server's profile cache is the only source left, and a UUID prefix is what remains when even that has no
     * record of them. [ASSUMPTION: showing the first 8 characters of the UUID beats hiding the row, matching
     * how {@code XppStatePayload.labelForPlayer} already falls back for an offline entry.]
     */
    private static String offlineName(UUID id) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            // Null when the server runs without a profile repository (offline-mode setups construct one all
            // the same, but nothing here may depend on that).
            GameProfileCache cache = server.getProfileCache();
            if (cache != null) {
                Optional<GameProfile> profile = cache.get(id);
                if (profile.isPresent() && !profile.get().getName().isBlank()) {
                    return profile.get().getName();
                }
            }
        }
        return id.toString().substring(0, 8);
    }

    private boolean managerLoaded() throws ReflectiveOperationException {
        return (Boolean) isManagerLoaded.invoke(api);
    }

    private Optional<?> teamForPlayer(UUID playerId) throws ReflectiveOperationException {
        return (Optional<?>) getTeamForPlayerId.invoke(getManager.invoke(api), playerId);
    }

    private Optional<?> teamById(UUID teamId) throws ReflectiveOperationException {
        return (Optional<?>) getTeamById.invoke(getManager.invoke(api), teamId);
    }

    private TeamInfo toInfo(Object team) throws ReflectiveOperationException {
        return new TeamInfo((UUID) teamGetId.invoke(team), ((Component) teamGetName.invoke(team)).getString());
    }
}
