package com.olynk.waystonexppayment.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Decides which of the three answers the server owes a viewer of a tank whose chunk is loaded.
 *
 * <p>The case worth spelling out is a team-based exclusion with no team mod installed: the player is not in the
 * tank's team list, but nobody could have looked up which teams they are in. Reporting {@code DENIED} there would
 * lock players out of tanks that in fact admit them, so the answer has to fall back to "not known".
 */
class HintAccessRuleTest {

    @Test
    void anAdmittedViewerIsAllowed() {
        assertEquals(XppTankHintPayload.Access.ALLOWED, HintAccessRule.of(true, true, true));
        assertEquals(XppTankHintPayload.Access.ALLOWED, HintAccessRule.of(true, false, true));
    }

    @Test
    void anExcludedViewerIsDeniedWhenTheTeamSystemAnswered() {
        assertEquals(XppTankHintPayload.Access.DENIED, HintAccessRule.of(false, true, true));
    }

    @Test
    void unknownTeamResolutionIsNotDenied() {
        assertEquals(XppTankHintPayload.Access.UNKNOWN, HintAccessRule.of(false, false, true));
    }

    @Test
    void noTeamEntriesMakeAnExclusionDecisiveEvenWithoutATeamMod() {
        // Nothing in the tank's access list is team-based, so the missing team system cannot explain the
        // exclusion: the player simply is not on the list.
        assertEquals(XppTankHintPayload.Access.DENIED, HintAccessRule.of(false, false, false));
    }
}
