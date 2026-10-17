package com.olynk.waystonexppayment.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The budget decides how often a teleport may pull a chunk in just to look at the tank under a waystone.
 *
 * <p>Two separate promises are under test: one attempt cannot spend more than its limit, and a slot whose load
 * produced nothing is not paid for again on the next click. A slot whose load did produce a tank is deliberately
 * not remembered - that is what makes a second trip to the same destination payable.
 */
class PayerBudgetTest {

    @AfterEach
    void clearMemo() {
        // The memo is deliberately static (it spans attempts, not just one), so the tests must not leak into
        // each other through it.
        PayerBudget.clearMemo();
    }

    @Test
    void stopsAfterItsLimit() {
        PayerBudget budget = new PayerBudget(1);

        assertTrue(budget.requestLoad("a", 1_000L));
        assertFalse(budget.requestLoad("b", 1_000L));
        assertEquals(1, budget.used());
        assertEquals(1, budget.limit());
    }

    @Test
    void zeroBudgetNeverLoads() {
        PayerBudget budget = new PayerBudget(0);

        assertFalse(budget.requestLoad("a", 1_000L));
        assertEquals(0, budget.used());
    }

    @Test
    void aLoadedTankIsNotRememberedAsAMiss() {
        assertTrue(new PayerBudget(1).requestLoad("a", 1_000L));

        // The chunk was resident right after the first trip, but the player has since left it; the next trip
        // must still be allowed to load it, or a working destination starts reporting "not enough experience".
        assertTrue(new PayerBudget(1).requestLoad("a", 3_000L));
    }

    @Test
    void aMissedLoadSuppressesRetriesInsideTheMemoWindow() {
        PayerBudget first = new PayerBudget(1);
        assertTrue(first.requestLoad("a", 1_000L));
        first.rememberMiss("a", 1_000L, 5_000L);

        PayerBudget retry = new PayerBudget(1);

        assertFalse(retry.requestLoad("a", 3_000L));
        assertEquals(0, retry.used(), "a memo hit must not be charged to the new attempt's limit");
    }

    @Test
    void memoExpires() {
        PayerBudget first = new PayerBudget(1);
        first.requestLoad("a", 1_000L);
        first.rememberMiss("a", 1_000L, 5_000L);

        PayerBudget later = new PayerBudget(1);

        assertTrue(later.requestLoad("a", 7_000L));
        assertEquals(1, later.used());
    }
}
