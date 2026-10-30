package com.olynk.waystonexppayment.fluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit conversions of a bucket rate: the arithmetic every payment goes through. */
class XpRateTest {

    @Test
    void oneXpPerMbIsOneThousandXpPerBucket() {
        XpRate rate = XpRate.parse(1000);

        assertEquals(1000, rate.xpOfMb(1000));
        assertEquals(1, rate.mbForXp(1));
        assertEquals(1, rate.mbPerXp());
    }

    @Test
    void aDiluteFluidChargesMoreMilliBucketsPerXp() {
        XpRate rate = XpRate.parse(0.5);

        assertEquals(2000, rate.mbForXp(1));
        assertEquals(1, rate.xpOfMb(2000));
        assertEquals(0, rate.xpOfMb(1999));
    }

    @Test
    void aConcentratedFluidChargesLessThanOneMbPerXp() {
        XpRate rate = XpRate.parse(2000);

        assertEquals(1, rate.mbForXp(2));
        assertEquals(2, rate.xpOfMb(1));
        assertEquals(1, rate.mbPerXp());
    }

    @Test
    void aPartialMilliBucketIsRoundedUpSoTheTankNeverUnderpays() {
        XpRate rate = XpRate.parse(1500);

        assertEquals(1, rate.mbForXp(1), "1 XP at 1.5 XP per mB is two thirds of an mB, rounded up");
        assertEquals(2, rate.mbForXp(3));
        assertEquals(1, rate.xpOfMb(1), "a single mB is only worth one whole XP at this rate");
        assertEquals(4, rate.xpOfMb(3));
    }

    @Test
    void anEmptyPoolIsWorthNothing() {
        XpRate rate = XpRate.parse(1000);

        assertEquals(0, rate.xpOfMb(0));
        assertEquals(0, rate.xpOfMb(-5));
        assertEquals(0, rate.mbForXp(0));
    }

    @Test
    void aRateOfZeroIsNotDeclarableAsARate() {
        assertThrows(IllegalArgumentException.class, () -> XpRate.parse(0));
    }

    @Test
    void aNegativeRateIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> XpRate.parse(-1));
    }

    @Test
    void aRateAboveTheCeilingIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> XpRate.parse(1_000_001));
    }

    @Test
    void aTinyRateIsStillRepresentable() {
        assertTrue(XpRate.parse(0.001).milliXpPerBucket() > 0);
    }

    @Test
    void describeShowsBothTheBucketRateAndTheLegacyUnit() {
        String described = XpRate.parse(1000).describe();

        assertTrue(described.contains("1000 XP/bucket"), described);
        assertTrue(described.contains("1 mB = 1 XP"), described);
    }
}
