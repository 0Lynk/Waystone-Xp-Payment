package com.olynk.waystonexppayment.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Mapping from a member reply's status byte to what the panel shows.
 *
 * <p>The interesting cases are the ones an empty member list cannot express: "no team mod" and "team gone"
 * both arrive with zero members and must not read as "this team has nobody in it".
 */
class XppMembersStatusTest {

    @Test
    void wireValuesAreStable() {
        assertEquals(0, XppMembersStatus.OK);
        assertEquals(1, XppMembersStatus.NO_TEAM_MOD);
        assertEquals(2, XppMembersStatus.UNKNOWN_TEAM);
        assertEquals(3, XppMembersStatus.NOT_OWNER);
    }

    @Test
    void anEmptyTeamIsNotEmptyByAccident() {
        assertEquals(XppMembersStatus.Phase.EMPTY, XppMembersStatus.phaseFor(XppMembersStatus.OK, 0));
        assertEquals(XppMembersStatus.Phase.OK, XppMembersStatus.phaseFor(XppMembersStatus.OK, 1));
        assertEquals(XppMembersStatus.Phase.OK, XppMembersStatus.phaseFor(XppMembersStatus.OK, 40));
    }

    @Test
    void failuresKeepTheirOwnCopy() {
        assertEquals(XppMembersStatus.Phase.NO_TEAM_MOD,
                XppMembersStatus.phaseFor(XppMembersStatus.NO_TEAM_MOD, 0));
        assertEquals(XppMembersStatus.Phase.UNKNOWN_TEAM,
                XppMembersStatus.phaseFor(XppMembersStatus.UNKNOWN_TEAM, 0));
        assertEquals(XppMembersStatus.Phase.DENIED,
                XppMembersStatus.phaseFor(XppMembersStatus.NOT_OWNER, 0));
    }

    @Test
    void anUnknownStatusIsReadAsNoTeamData() {
        // Only a peer that does not speak this protocol version can send one, and there is no member data to
        // show in that case either way.
        assertEquals(XppMembersStatus.Phase.NO_TEAM_MOD, XppMembersStatus.phaseFor((byte) 99, 5));
    }

    @Test
    void onlyTheRowStateHasNoSentence() {
        assertNull(XppMembersStatus.messageKey(XppMembersStatus.Phase.OK));
        assertEquals("access.members.loading", XppMembersStatus.messageKey(XppMembersStatus.Phase.LOADING));
        assertEquals("access.members.empty", XppMembersStatus.messageKey(XppMembersStatus.Phase.EMPTY));
        assertEquals("access.members.no_team_mod",
                XppMembersStatus.messageKey(XppMembersStatus.Phase.NO_TEAM_MOD));
        assertEquals("access.members.unknown_team",
                XppMembersStatus.messageKey(XppMembersStatus.Phase.UNKNOWN_TEAM));
        assertEquals("access.members.denied", XppMembersStatus.messageKey(XppMembersStatus.Phase.DENIED));
        assertEquals("access.members.timeout", XppMembersStatus.messageKey(XppMembersStatus.Phase.TIMEOUT));
    }

    @Test
    void onlyTheTimeoutOffersARetry() {
        assertEquals("access.members.retry", XppMembersStatus.secondLineKey(XppMembersStatus.Phase.TIMEOUT));
        assertNull(XppMembersStatus.secondLineKey(XppMembersStatus.Phase.LOADING));
        assertNull(XppMembersStatus.secondLineKey(XppMembersStatus.Phase.OK));
        assertNull(XppMembersStatus.secondLineKey(XppMembersStatus.Phase.EMPTY));
    }
}
