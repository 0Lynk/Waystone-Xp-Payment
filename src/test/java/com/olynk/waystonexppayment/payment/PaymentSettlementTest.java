package com.olynk.waystonexppayment.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.olynk.waystonexppayment.fluid.XpRate;

/**
 * What the bill looks like when the tank cannot honour the plan it agreed to.
 *
 * <p>The teleport has already happened by the time these numbers are computed, so every case here answers the
 * same question: who pays what now, and how much of it is nobody's.
 */
class PaymentSettlementTest {

    private static final XpRate ONE_XP_PER_MB = XpRate.ofBucket(1000);

    private static final XpRate TEN_MB_PER_XP = XpRate.parse(100);

    @Test
    void theTankDeliveringTheWholePlanChargesThePlayerOnlyTheirShare() {
        PaymentSplit plan = PaymentSplit.compute(30, 12, ONE_XP_PER_MB, 50);

        PaymentSettlement settlement = PaymentSettlement.settle(plan, plan.fluidUsedMb(), 50);

        assertEquals(12, settlement.xpFromTank());
        assertEquals(18, settlement.xpFromPlayer());
        assertTrue(settlement.isComplete());
    }

    @Test
    void aTankThatRanDryMovesTheGapToThePlayer() {
        PaymentSplit plan = PaymentSplit.compute(30, 12, ONE_XP_PER_MB, 50);

        PaymentSettlement settlement = PaymentSettlement.settle(plan, 0, 50);

        assertEquals(0, settlement.xpFromTank());
        assertEquals(30, settlement.xpFromPlayer());
        assertTrue(settlement.isComplete());
    }

    @Test
    void aPartialDrainIsPaidByThePlayerUpToWhatTheyHold() {
        PaymentSplit plan = PaymentSplit.compute(30, 12, ONE_XP_PER_MB, 50);

        PaymentSettlement settlement = PaymentSettlement.settle(plan, 5, 20);

        assertEquals(5, settlement.xpFromTank());
        assertEquals(20, settlement.xpFromPlayer());
        assertEquals(5, settlement.xpStillOwed(), "25 owed, the player holds 20 - the last 5 cannot be paid");
        assertFalse(settlement.isComplete());
    }

    @Test
    void anEmptyPlayerStillOwesWhatTheTankCouldNotPay() {
        PaymentSplit plan = PaymentSplit.compute(20, 20, ONE_XP_PER_MB, 0);

        PaymentSettlement settlement = PaymentSettlement.settle(plan, 0, 0);

        assertEquals(0, settlement.xpFromTank());
        assertEquals(0, settlement.xpFromPlayer());
        assertEquals(20, settlement.xpStillOwed());
    }

    @Test
    void aTankCreditedForMoreThanTheCostIsCapped() {
        PaymentSplit plan = PaymentSplit.compute(10, 100, ONE_XP_PER_MB, 0);

        PaymentSettlement settlement = PaymentSettlement.settle(plan, 500, 0);

        assertEquals(10, settlement.xpFromTank(), "the tank can only ever be credited for the cost itself");
        assertEquals(0, settlement.xpStillOwed());
    }

    @Test
    void theRateAppliesToWhatTheTankActuallyGave() {
        PaymentSplit plan = PaymentSplit.compute(10, 100, TEN_MB_PER_XP, 5);

        PaymentSettlement settlement = PaymentSettlement.settle(plan, 95, 5);

        assertEquals(9, settlement.xpFromTank(), "95 mB at 10 mB per XP is nine whole XP, not ten");
        assertEquals(1, settlement.xpFromPlayer());
        assertTrue(settlement.isComplete());
    }

    @Test
    void aNegativeDrainCountsAsNothingGiven() {
        PaymentSplit plan = PaymentSplit.compute(10, 100, ONE_XP_PER_MB, 0);

        PaymentSettlement settlement = PaymentSettlement.settle(plan, -5, 10);

        assertEquals(0, settlement.xpFromTank());
        assertEquals(10, settlement.xpFromPlayer());
    }
}
