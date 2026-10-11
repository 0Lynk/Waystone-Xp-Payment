package com.olynk.waystonexppayment.auth;

import java.util.List;
import java.util.UUID;

import com.olynk.waystonexppayment.net.XppMembersStatus;

import net.minecraft.world.entity.player.Player;

/**
 * Bridge to an optional team mod. The tank authorisation model only needs "which teams is this player
 * in" plus, for the GUI team picker, "which teams exist". A resolver that knows nothing reports none.
 */
public interface TeamResolver {

    record TeamInfo(UUID id, String name) {
    }

    /**
     * One member of a team, named as well as the server can name them.
     *
     * <p>{@code name} is display only and may be a UUID prefix: no identity is ever matched on it.
     */
    record MemberInfo(UUID id, String name, boolean online) {
    }

    /**
     * Why a member lookup answered what it answered.
     *
     * <p>The status is carried next to the list because an empty list is ambiguous - "this team has nobody in
     * it" and "this server has no team mod" are different sentences on screen, and only the status tells them
     * apart.
     */
    record MembersResult(byte status, List<MemberInfo> members) {

        public static MembersResult noTeamMod() {
            return new MembersResult(XppMembersStatus.NO_TEAM_MOD, List.of());
        }

        public static MembersResult unknownTeam() {
            return new MembersResult(XppMembersStatus.UNKNOWN_TEAM, List.of());
        }

        public static MembersResult of(List<MemberInfo> members) {
            return new MembersResult(XppMembersStatus.OK, members);
        }
    }

    /** Resolver used when no team mod is installed. */
    TeamResolver NONE = new TeamResolver() {
        @Override
        public List<TeamInfo> teamsOf(Player player) {
            return List.of();
        }

        @Override
        public List<TeamInfo> allTeams() {
            return List.of();
        }
    };

    /** Teams the player currently belongs to. Never null. */
    List<TeamInfo> teamsOf(Player player);

    /**
     * Teams an arbitrary player id belongs to, for callers that have no {@link Player} at hand.
     *
     * <p>Used by the tank owner check, which resolves the owner's teams while the owner is offline. Defaults
     * to no teams so a resolver that cannot look players up by id stays correct, just more restrictive.
     */
    default List<TeamInfo> teamsOf(UUID playerId) {
        return List.of();
    }

    /** Every team known to the team mod, for pickers. Never null. */
    List<TeamInfo> allTeams();

    /**
     * Members of a team the caller named by id, for the member panel.
     *
     * <p>Defaults to "no team mod" rather than an empty list: a resolver that cannot answer has to say so,
     * because the panel words those two cases differently.
     */
    default MembersResult membersOf(UUID teamId) {
        return MembersResult.noTeamMod();
    }
}
