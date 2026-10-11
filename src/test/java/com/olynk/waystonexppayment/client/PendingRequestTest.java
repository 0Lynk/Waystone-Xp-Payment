package com.olynk.waystonexppayment.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.olynk.waystonexppayment.client.PendingRequest.Entry;
import com.olynk.waystonexppayment.client.PendingRequest.Verdict;
import com.olynk.waystonexppayment.net.XppAccessPayload.Action;

/**
 * How a snapshot proves what happened to the edit in flight.
 *
 * <p>The server answers every request with a snapshot, refusals included, so this judgement is the only thing
 * that decides between "applied", "refused" and "not answered yet".
 */
class PendingRequestTest {

    private static final UUID TARGET = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID OTHER = UUID.fromString("99999999-8888-7777-6666-555555555555");

    private static PendingRequest request(Action action, UUID target, String label, boolean whitelist) {
        return new PendingRequest(action, Collections.singletonList(target), label, whitelist, 0L);
    }

    @Test
    void anAddedPlayerShowsUpInTheSnapshot() {
        PendingRequest add = request(Action.ADD_PLAYER, TARGET, "Steve", true);

        assertEquals(Verdict.APPLIED, add.verdict(List.of(new Entry(TARGET, "Steve", false)), true));
        assertEquals(Verdict.REFUSED, add.verdict(List.of(new Entry(OTHER, "Alex", false)), true));
        assertEquals(Verdict.REFUSED, add.verdict(List.of(), true));
    }

    @Test
    void aTeamEntryIsNotAPlayerEntry() {
        // The two share a record, so only the flag can tell them apart - matching the wrong one would report a
        // success that never happened.
        PendingRequest add = request(Action.ADD_PLAYER, TARGET, "Team", false);

        assertEquals(Verdict.REFUSED, add.verdict(List.of(new Entry(TARGET, "Team", true)), true));
        assertEquals(Verdict.APPLIED, add.verdict(List.of(new Entry(TARGET, "Team", false)), true));
    }

    @Test
    void anAddedTeamShowsUpInTheSnapshot() {
        PendingRequest add = request(Action.ADD_TEAM, TARGET, "Builders", true);

        assertEquals(Verdict.APPLIED, add.verdict(List.of(new Entry(TARGET, "Builders", true)), true));
        assertEquals(Verdict.REFUSED, add.verdict(List.of(new Entry(TARGET, "Builders", false)), true));
    }

    @Test
    void aRemovalIsProvenByAbsence() {
        PendingRequest remove = request(Action.REMOVE_PLAYER, TARGET, "Steve", true);

        assertEquals(Verdict.APPLIED, remove.verdict(List.of(new Entry(OTHER, "Alex", false)), true));
        assertEquals(Verdict.REFUSED, remove.verdict(List.of(new Entry(TARGET, "Steve", false)), true));
    }

    @Test
    void aRemovedTeamIsAlsoMatchedByItsName() {
        PendingRequest remove = request(Action.REMOVE_TEAM, TARGET, "Builders", true);

        assertEquals(Verdict.APPLIED, remove.verdict(List.of(new Entry(OTHER, "Miners", true)), true));
        assertEquals(Verdict.REFUSED, remove.verdict(List.of(new Entry(TARGET, "Renamed", true)), true));
    }

    @Test
    void aLegacyRemovalFallsBackToTheLabel() {
        // A row stored before ids existed carries no id, so the label it was sent with is the only identity the
        // snapshot can be searched for.
        PendingRequest legacy = request(Action.REMOVE_PLAYER, null, "Steve", true);

        assertEquals(Verdict.APPLIED, legacy.verdict(List.of(new Entry(null, "Alex", false)), true));
        assertEquals(Verdict.REFUSED, legacy.verdict(List.of(new Entry(null, "Steve", false)), true));
        assertEquals(Verdict.REFUSED, legacy.verdict(List.of(new Entry(null, "steve", false)), true),
                "the server matches legacy names case-insensitively, so this must too");
    }

    @Test
    void aModeChangeIsJudgedByTheModeItself() {
        PendingRequest mode = request(Action.SET_MODE, null, "blacklist", false);

        assertEquals(Verdict.APPLIED, mode.verdict(List.of(), false));
        assertEquals(Verdict.REFUSED, mode.verdict(List.of(), true));
    }

    @Test
    void aBulkRequestWaitsForItsLastAnswer() {
        // "Add all my teams" sends one packet per team, and the first snapshot back only shows the first of
        // them: reading that as a refusal would report a failure for a request that is going through.
        PendingRequest bulk = new PendingRequest(Action.ADD_TEAM, List.of(TARGET, OTHER), "", true, 0L);

        assertEquals(Verdict.INCOMPLETE, bulk.verdict(List.of(new Entry(TARGET, "Builders", true)), true));
        assertEquals(Verdict.INCOMPLETE, bulk.verdict(List.of(), true));
        assertEquals(Verdict.APPLIED,
                bulk.verdict(List.of(new Entry(TARGET, "Builders", true), new Entry(OTHER, "Miners", true)),
                        true));
    }

    @Test
    void aSingleTargetRequestNeverStaysInFlight() {
        // One packet, one snapshot: a miss there is an answer.
        PendingRequest single = request(Action.ADD_TEAM, TARGET, "Builders", true);

        assertEquals(Verdict.REFUSED, single.verdict(List.of(), true));
    }

    @Test
    void theInFlightRowIsFoundById() {
        PendingRequest add = request(Action.ADD_PLAYER, TARGET, "Steve", true);

        assertTrue(add.concerns(TARGET, "Anything", false));
        assertFalse(add.concerns(OTHER, "Steve", false));
    }

    @Test
    void anIdlessRequestOnlyClaimsTheRowItNamed() {
        PendingRequest legacy = request(Action.REMOVE_PLAYER, null, "Steve", true);

        assertTrue(legacy.concerns(null, "Steve", false));
        assertFalse(legacy.concerns(null, "Alex", false));
        assertFalse(legacy.concerns(TARGET, "Steve", false),
                "an id-bearing row is never the legacy row the request named");
    }

    @Test
    void anEmptyLabelClaimsNoRow() {
        // "Add me" carries no label; without this an id-less row would look like the one being changed.
        PendingRequest add = request(Action.ADD_PLAYER, TARGET, "", true);

        assertFalse(add.concerns(null, "", false));
        assertTrue(add.concerns(TARGET, "", false));
    }
}
