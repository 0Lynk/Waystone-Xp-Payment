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

    @Test
    void aGapTheTankCanStillAbsorbIsWorthFilling() {
        // 10 XP/bucket holds 1000 mB for 10 XP, so a 17 XP gap is 1700 mB and 3000 mB are still free.
        PoolQuote quote = PoolQuote.of(27, 10, TEN_XP_PER_BUCKET, 0);

        assertTrue(quote.topUpWouldCloseTheGap(4000));
    }

    @Test
    void aGapBeyondTheFreeRoomIsNotWorthFilling() {
        PoolQuote quote = PoolQuote.of(27, 10, TEN_XP_PER_BUCKET, 0);

        assertFalse(quote.topUpWouldCloseTheGap(2000));
    }

    @Test
    void aFullTankCannotAbsorbAnything() {
        // 40 XP is exactly a full 4000 mB of a 10 XP/bucket fluid, so a 100 XP fare is 60 XP beyond reach.
        PoolQuote quote = PoolQuote.of(100, 40, TEN_XP_PER_BUCKET, 0);

        assertEquals(6000, quote.missingMb());
        assertFalse(quote.topUpWouldCloseTheGap(4000));
    }
}
