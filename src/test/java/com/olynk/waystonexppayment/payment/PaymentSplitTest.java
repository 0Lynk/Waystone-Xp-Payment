package com.olynk.waystonexppayment.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Pure math of the "tank first, player second, reject if neither covers it" rule. */
class PaymentSplitTest {

    @Test
    void poolCoversWholeCost() {
        PaymentSplit split = PaymentSplit.compute(30, 100, 1, 0);

        assertEquals(30, split.costXp());
        assertEquals(30, split.xpFromFluid());
        assertEquals(30, split.fluidUsedMb());
        assertEquals(0, split.xpFromPlayer());
        assertTrue(split.affordable());
    }

    @Test
    void poolCoversPartAndPlayerPaysTheRest() {
        PaymentSplit split = PaymentSplit.compute(30, 12, 1, 50);

        assertEquals(12, split.xpFromFluid());
        assertEquals(12, split.fluidUsedMb());
        assertEquals(18, split.xpFromPlayer());
        assertTrue(split.affordable());
    }

    @Test
    void neitherSideCoversCost() {
        PaymentSplit split = PaymentSplit.compute(30, 5, 1, 10);

        assertEquals(5, split.xpFromFluid());
        assertEquals(25, split.xpFromPlayer());
        assertFalse(split.affordable(), "an unaffordable split must be rejected before anything is consumed");
    }

    @Test
    void rateAboveOneIsAppliedToTheFluidPart() {
        PaymentSplit split = PaymentSplit.compute(10, 100, 10, 0);

        assertEquals(10, split.xpFromFluid());
        assertEquals(100, split.fluidUsedMb(), "10 XP at 10 mB per XP consumes exactly 100 mB");
        assertTrue(split.affordable());
    }

    @Test
    void poolXpIsNotRoundedUpWhenItPartiallyCoversTheCost() {
        PaymentSplit split = PaymentSplit.compute(7, 3, 1, 0);

        assertEquals(3, split.xpFromFluid());
        assertEquals(4, split.xpFromPlayer());
        assertFalse(split.affordable());
    }

    @Test
    void zeroCostIsANoop() {
        PaymentSplit split = PaymentSplit.compute(0, 0, 1, 0);

        assertTrue(split.isNoop());
        assertTrue(split.affordable());
        assertEquals(0, split.fluidUsedMb());
    }

    @Test
    void negativeInputsAreClampedToZero() {
        PaymentSplit split = PaymentSplit.compute(-5, -1, 1, -3);

        assertEquals(0, split.costXp());
        assertEquals(0, split.fluidUsedMb());
        assertEquals(0, split.xpFromPlayer());
        assertTrue(split.affordable());
    }

    @Test
    void rateMustBeAtLeastOne() {
        assertThrows(IllegalArgumentException.class, () -> PaymentSplit.compute(10, 10, 0, 10));
    }
}
