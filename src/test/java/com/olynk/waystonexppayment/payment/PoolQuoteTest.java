package com.olynk.waystonexppayment.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.olynk.waystonexppayment.fluid.XpRate;
import org.junit.jupiter.api.Test;

/**
 * The numbers a player sees and the numbers the bill is settled from come from one quote.
 *
 * <p>The case worth locking is the cheap fluid one: a tank holding a thousand mB of a 10 XP/bucket fluid is worth
 * 10 XP, so a 27 XP trip has to report the 1700 mB gap instead of looking like a broken tank.
 */
class PoolQuoteTest {

    private static final XpRate ONE_XP_PER_MB = XpRate.ofBucket(1000);
    private static final XpRate TEN_XP_PER_BUCKET = XpRate.ofBucket(10);

    @Test
    void aTankThatCoversTheWholeCostAsksNothingOfThePlayer() {
        PoolQuote quote = PoolQuote.of(27, 100, ONE_XP_PER_MB, 0);

        assertEquals(27, quote.xpFromTank());
        assertEquals(0, quote.xpFromPlayer());
        assertEquals(27, quote.tankMb());
        assertEquals(0, quote.missingXp());
        assertTrue(quote.affordable());
        assertTrue(quote.tankCoversAll());
    }

    @Test
    void aCheapFluidTankIsWorthLessThanTheSameAmountOfRichFluid() {
        PoolQuote cheap = PoolQuote.of(27, 10, TEN_XP_PER_BUCKET, 0);
        PoolQuote rich = PoolQuote.of(27, 10, ONE_XP_PER_MB, 0);

        assertEquals(10, cheap.xpFromTank());
        assertEquals(17, cheap.missingXp());
        assertFalse(cheap.affordable());
        assertEquals(10, rich.xpFromTank());
        assertFalse(rich.affordable());
    }

    @Test
    void theShortfallIsReportedInMillibuckets() {
        PoolQuote quote = PoolQuote.of(27, 10, TEN_XP_PER_BUCKET, 0);

        // 10 XP/bucket is 100 mB per XP, so the 17 XP the tank cannot cover need 1700 mB to be worth the same.
        assertEquals(1700, quote.missingMb());
    }

    @Test
    void aPlayerWhoCanTopUpTheCheapTankMakesTheTripAffordable() {
        PoolQuote quote = PoolQuote.of(27, 10, TEN_XP_PER_BUCKET, 17);

        assertTrue(quote.affordable());
        assertEquals(0, quote.missingXp());
        assertEquals(0, quote.missingMb());
        assertFalse(quote.tankCoversAll());
    }

    @Test
    void anEmptyTankQuotesTheWholeCostToThePlayer() {
        PoolQuote quote = PoolQuote.of(27, 0, TEN_XP_PER_BUCKET, 30);

        assertEquals(0, quote.xpFromTank());
        assertEquals(27, quote.xpFromPlayer());
        assertEquals(0, quote.tankMb());
        assertTrue(quote.affordable());
    }

    @Test
    void aFractionOfAMillibucketIsRoundedUpSoTheTankNeverUnderpays() {
        PoolQuote quote = PoolQuote.of(1, 1, XpRate.parse(1.5), 0);

        assertEquals(667, quote.tankMb());
        assertTrue(quote.affordable());
    }

    @Test
    void aZeroCostTripNeedsNothing() {
        PoolQuote quote = PoolQuote.of(0, 0, TEN_XP_PER_BUCKET, 0);

        assertEquals(0, quote.missingXp());
        assertTrue(quote.affordable());
        assertTrue(quote.tankCoversAll());
    }

    @Test
    void negativeInputsAreClampedToZero() {
        PoolQuote quote = PoolQuote.of(-5, -10, ONE_XP_PER_MB, -3);

        assertEquals(0, quote.costXp());
        assertEquals(0, quote.poolXp());
        assertEquals(0, quote.missingXp());
        assertTrue(quote.affordable());
    }
}
