package com.olynk.waystonexppayment.net;

import com.olynk.waystonexppayment.net.XppTankHintPayload.Access;

/**
 * Decides the access tri-state of one hint without touching the world.
 *
 * <p>Kept apart from the handler so the rule can be read and tested on its own: the whole point of the third
 * state is that "this player may not pay from that tank" and "this server cannot tell" are different answers,
 * and only one of them may hide the tank from the list.
 */
public final class HintAccessRule {

    private HintAccessRule() {
    }

    /**
     * @param allowed           the tank's access list admits this player
     * @param teamSystemAvailable a team mod answered the membership question
     * @param anyTeamEntries    the access list names at least one team
     * @return {@link Access#ALLOWED}, or {@link Access#DENIED} when the exclusion is provable here
     */
    public static Access of(boolean allowed, boolean teamSystemAvailable, boolean anyTeamEntries) {
        if (allowed) {
            return Access.ALLOWED;
        }
        if (anyTeamEntries && !teamSystemAvailable) {
            // WHY: without a team mod the list can name teams nobody can match against, so "not listed" is not
            // evidence of exclusion. Answering DENIED here would hide a tank the player may well be entitled to
            // and would stop the client from ever asking the server again.
            return Access.UNKNOWN;
        }
        return Access.DENIED;
    }
}
