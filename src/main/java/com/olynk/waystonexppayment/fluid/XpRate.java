package com.olynk.waystonexppayment.fluid;

/**
 * How much experience one bucket (1000 mB) of a fluid is worth.
 *
 * <p>Stored as milli-XP per bucket so that a fractional rate is exact: {@code 500} is half an XP per bucket
 * ({@code 0.5}), while a whole number such as {@code 1000} is one XP per mB. Money never goes through a float,
 * because a rate that is "almost" right silently over- or undercharges every teleport.
 */
public record XpRate(long milliXpPerBucket) {

    /** One XP expressed in the milli unit. */
    public static final long MILLI_PER_XP = 1000L;

    /** One bucket expressed in mB. */
    public static final long MB_PER_BUCKET = 1000L;

    /** Upper bound of a declarable rate: one mB worth 1000 XP. */
    public static final long MAX_MILLI_XP_PER_BUCKET = 1_000_000L * MILLI_PER_XP;

    public XpRate {
        if (milliXpPerBucket < 0) {
            throw new IllegalArgumentException("milliXpPerBucket must not be negative, was " + milliXpPerBucket);
        }
        if (milliXpPerBucket > MAX_MILLI_XP_PER_BUCKET) {
            throw new IllegalArgumentException(
                    "milliXpPerBucket must not exceed " + MAX_MILLI_XP_PER_BUCKET + ", was " + milliXpPerBucket);
        }
    }

    /** @param xpPerBucket whole XP per bucket, e.g. {@code 1000} for one XP per mB */
    public static XpRate ofBucket(int xpPerBucket) {
        return new XpRate(Math.multiplyExact((long) xpPerBucket, MILLI_PER_XP));
    }

    /**
     * @param xpPerBucket XP per bucket as written in a datapack, e.g. {@code 0.5} or {@code 2000}
     * @throws IllegalArgumentException if the value is not a positive rate a datapack may declare
     */
    public static XpRate parse(double xpPerBucket) {
        if (!Double.isFinite(xpPerBucket) || xpPerBucket <= 0) {
            throw new IllegalArgumentException("xp_per_bucket must be a positive number, was " + xpPerBucket);
        }
        long milli = Math.round(xpPerBucket * MILLI_PER_XP);
        if (milli <= 0) {
            throw new IllegalArgumentException("xp_per_bucket is too small to represent, was " + xpPerBucket);
        }
        return new XpRate(milli);
    }

    /** XP a full tank of {@code mb} milli-buckets is worth, floored: the remainder stays in the tank. */
    public int xpOfMb(int mb) {
        if (mb <= 0) {
            return 0;
        }
        long xp = Math.multiplyExact((long) mb, milliXpPerBucket) / (MILLI_PER_XP * MB_PER_BUCKET);
        return (int) Math.min(xp, Integer.MAX_VALUE);
    }

    /** Milli-buckets needed to pay {@code xp} experience points, rounded up so the tank never underpays. */
    public int mbForXp(int xp) {
        if (xp <= 0) {
            return 0;
        }
        long mb = Math.multiplyExact((long) xp, MILLI_PER_XP * MB_PER_BUCKET) / milliXpPerBucket;
        long remainder = Math.multiplyExact((long) xp, MILLI_PER_XP * MB_PER_BUCKET) % milliXpPerBucket;
        if (remainder > 0) {
            mb++;
        }
        return (int) Math.min(mb, Integer.MAX_VALUE);
    }

    /** The legacy unit: how many mB are worth one XP, rounded up. Only for display and old-save conversion. */
    public int mbPerXp() {
        return (int) Math.max(1L, (MILLI_PER_XP * MB_PER_BUCKET + milliXpPerBucket - 1) / milliXpPerBucket);
    }

    /** Human readable rate, e.g. {@code 1 XP/bucket (1 mB = 1 XP)}. */
    public String describe() {
        String perBucket = milliXpPerBucket % MILLI_PER_XP == 0
                ? Long.toString(milliXpPerBucket / MILLI_PER_XP)
                : String.format(java.util.Locale.ROOT, "%.3f", milliXpPerBucket / (double) MILLI_PER_XP);
        return perBucket + " XP/bucket (" + mbPerXp() + " mB = 1 XP)";
    }
}
