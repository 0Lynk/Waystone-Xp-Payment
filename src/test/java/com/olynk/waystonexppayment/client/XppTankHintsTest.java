package com.olynk.waystonexppayment.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * When a server answer about a far-away tank is old enough to be asked about again, and how long a question is
 * worth waiting for.
 *
 * <p>What this guards: an answer that is only trusted for a few seconds must not be dropped when it gets older - a
 * dropped answer reads as "no information", and the button then treats an empty tank as one that can pay. It has to
 * be kept and refreshed instead, at a rate that cannot flood the server with one query per frame per entry. The same
 * "no information" rule is why an outstanding question is reported separately: a slot that was just asked about has
 * no reading yet, and calling that "can pay" lights the entry for a tick and takes it back.
 */
class XppTankHintsTest {

    private static final long NOW = 1_000_000L;
    private static final long TRUSTED = 5_000L;
    private static final long REASK = 1_000L;
    private static final long WAIT = 1_000L;

    @Test
    void aFreshAnswerIsNotAskedAboutAgain() {
        assertFalse(XppTankHints.dueForReask(NOW - TRUSTED, null, NOW));
    }

    @Test
    void aStaleAnswerThatWasNeverReaskedIsDue() {
        assertTrue(XppTankHints.dueForReask(NOW - TRUSTED - 1, null, NOW));
    }

    @Test
    void aStaleAnswerIsNotReaskedOnEveryFrame() {
        assertFalse(XppTankHints.dueForReask(NOW - TRUSTED - 1, NOW - REASK + 500, NOW));
    }

    @Test
    void aStaleAnswerIsReaskedOnceTheIntervalHasPassed() {
        assertTrue(XppTankHints.dueForReask(NOW - TRUSTED - 1, NOW - REASK, NOW));
    }

    @Test
    void theBoundaryOfTrustRefreshesOnlyOnceItIsPast() {
        assertFalse(XppTankHints.dueForReask(NOW - TRUSTED, null, NOW));
        assertTrue(XppTankHints.dueForReask(NOW - TRUSTED - 1, null, NOW));
    }

    @Test
    void aListBuiltOnASecondsOldAnswerAsksAgain() {
        assertTrue(XppTankHints.dueForListOpen(NOW - TRUSTED - 1, NOW));
    }

    @Test
    void aListBuiltOnAnAnswerFromAMomentAgoDoesNotAskAgain() {
        assertFalse(XppTankHints.dueForListOpen(NOW - 200L, NOW));
    }

    @Test
    void aSlotNobodyAskedAboutIsNotWaitedFor() {
        assertFalse(XppTankHints.stillWaiting(null, NOW));
    }

    @Test
    void aQuestionInFlightIsWaitedFor() {
        assertTrue(XppTankHints.stillWaiting(NOW - 200L, NOW));
    }

    @Test
    void waitingEndsWhenTheAnswerIsOverdue() {
        assertTrue(XppTankHints.stillWaiting(NOW - WAIT, NOW));
        assertFalse(XppTankHints.stillWaiting(NOW - WAIT - 1, NOW));
    }
}
