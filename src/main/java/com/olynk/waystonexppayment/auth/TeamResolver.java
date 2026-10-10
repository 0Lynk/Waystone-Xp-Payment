package com.olynk.waystonexppayment.auth;

import java.util.List;
import java.util.UUID;

import net.minecraft.world.entity.player.Player;

/**
 * Bridge to an optional team mod. The tank authorisation model only needs "which teams is this player
 * in" plus, for the GUI team picker, "which teams exist". A resolver that knows nothing reports none.
 */
public interface TeamResolver {

    record TeamInfo(UUID id, String name) {
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

    /** Every team known to the team mod, for pickers. Never null. */
    List<TeamInfo> allTeams();
}
