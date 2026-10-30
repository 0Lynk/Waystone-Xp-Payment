package com.olynk.waystonexppayment.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.olynk.waystonexppayment.fluid.XpRate;

/** Pure math of the "tank first, player second, reject if neither covers it" rule. */
class PaymentSplitTest {

    /** The classic 1 mB = 1 XP fluid. */
    private static final XpRate ONE_XP_PER_MB = XpRate.ofBucket(1000);

    /** A ten times weaker fluid: 10 mB per XP. */
    private static final XpRate TEN_MB_PER_XP = XpRate.parse(100);

    @Test
    void poolCoversWholeCost() {
        PaymentSplit split = PaymentSplit.compute(30, 100, ONE_XP_PER_MB, 0);

        assertEquals(30, split.costXp());
        assertEquals(30, split.xpFromFluid());
        assertEquals(30, split.fluidUsedMb());
        assertEquals(0, split.xpFromPlayer());
        assertTrue(split.affordable());
    }

    @Test
    void poolCoversPartAndPlayerPaysTheRest() {
        PaymentSplit split = PaymentSplit.compute(30, 12, ONE_XP_PER_MB, 50);

        assertEquals(12, split.xpFromFluid());
        assertEquals(12, split.fluidUsedMb());
        assertEquals(18, split.xpFromPlayer());
        assertTrue(split.affordable());
    }

    @Test
    void neitherSideCoversCost() {
        PaymentSplit split = PaymentSplit.compute(30, 5, ONE_XP_PER_MB, 10);

        assertEquals(5, split.xpFromFluid());
        assertEquals(25, split.xpFromPlayer());
        assertFalse(split.affordable(), "an unaffordable split must be rejected before anything is consumed");
    }

    @Test
    void aWeakerFluidIsChargedMoreMillibuckets() {
        PaymentSplit split = PaymentSplit.compute(10, 100, TEN_MB_PER_XP, 0);

        assertEquals(10, split.xpFromFluid());
        assertEquals(100, split.fluidUsedMb(), "10 XP at 10 mB per XP consumes exactly 100 mB");
        assertTrue(split.affordable());
    }

    @Test
    void aStrongerFluidIsChargedFewerMillibuckets() {
        PaymentSplit split = PaymentSplit.compute(10, 100, XpRate.parse(2000), 0);

        assertEquals(10, split.xpFromFluid());
        assertEquals(5, split.fluidUsedMb(), "at 2 XP per mB the same 10 XP costs 5 mB");
    }

    @Test
    void aFractionOfAMillibucketIsRoundedUpSoTheTankNeverUnderpays() {
        PaymentSplit split = PaymentSplit.compute(3, 100, XpRate.parse(1500), 0);
        PaymentSplit soloSplit = PaymentSplit.compute(1, 100, XpRate.parse(1500), 0);

        assertEquals(1, soloSplit.fluidUsedMb(), "two thirds of an mB must be rounded up, not down to zero");

        assertEquals(3, split.xpFromFluid());
        assertEquals(2, split.fluidUsedMb(), "1.5 XP per mB needs 2 mB to cover 3 XP");
    }

    @Test
    void poolXpIsNotRoundedUpWhenItPartiallyCoversTheCost() {
        PaymentSplit split = PaymentSplit.compute(7, 3, ONE_XP_PER_MB, 0);

        assertEquals(3, split.xpFromFluid());
        assertEquals(4, split.xpFromPlayer());
        assertFalse(split.affordable());
    }

    @Test
    void zeroCostIsANoop() {
        PaymentSplit split = PaymentSplit.compute(0, 0, ONE_XP_PER_MB, 0);

        assertTrue(split.isNoop());
        assertTrue(split.affordable());
        assertEquals(0, split.fluidUsedMb());
    }

    @Test
    void negativeInputsAreClampedToZero() {
        PaymentSplit split = PaymentSplit.compute(-5, -1, ONE_XP_PER_MB, -3);

        assertEquals(0, split.costXp());
        assertEquals(0, split.fluidUsedMb());
        assertEquals(0, split.xpFromPlayer());
        assertTrue(split.affordable());
    }

    @Test
    void aSplitWithoutARateIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> PaymentSplit.compute(10, 10, null, 10));
    }

    @Test
    void anEmptyPoolIsExactlyTheVanillaRule() {
        PaymentSplit enough = PaymentSplit.compute(27, 0, ONE_XP_PER_MB, 27);
        PaymentSplit notEnough = PaymentSplit.compute(27, 0, ONE_XP_PER_MB, 26);

        assertTrue(enough.affordable(), "an empty tank must never grey a trip the player can pay for alone");
        assertFalse(notEnough.affordable());
        assertEquals(0, enough.fluidUsedMb());
        assertEquals(27, enough.xpFromPlayer());
    }
}
