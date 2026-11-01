package com.olynk.waystonexppayment.client;

/**
 * Eases the rendered fluid level towards the level the tank really holds.
 *
 * <p>Create animates its tanks this way and Ender IO does not; without it the surface jumps a whole bucket the
 * instant a pipe finishes a transfer, which reads as a glitch rather than as liquid settling.
 *
 * <p>The clock is passed in so the curve can be tested without a game.
 */
public final class FillAnimation {

    /** Time to close about 63% of the gap: a change of one bucket settles in roughly half a second. */
    private static final float TIME_CONSTANT_MILLIS = 180F;

    /** Below this the remaining gap is invisible, and chasing it would keep the level "moving" forever. */
    private static final float SNAP = 0.0005F;

    /** A pause (chunk unload, lag spike) should not teleport the surface; one second is plenty of catch-up. */
    private static final long LONGEST_STEP_MILLIS = 1_000L;

    private float shown;
    private long lastMillis = Long.MIN_VALUE;

    /**
     * Advances the animation to {@code nowMillis} and returns the level to draw, in {@code 0..1}.
     *
     * <p>The first call snaps to the target: a tank that was already full when it came into view has nothing to
     * animate, and starting from zero would look like it is filling up on its own.
     */
    public float advance(float target, long nowMillis) {
        float clamped = Math.min(1F, Math.max(0F, target));
        if (lastMillis == Long.MIN_VALUE) {
            lastMillis = nowMillis;
            shown = clamped;
            return shown;
        }
        long elapsed = Math.min(LONGEST_STEP_MILLIS, Math.max(0L, nowMillis - lastMillis));
        lastMillis = nowMillis;
        shown += (clamped - shown) * (1F - (float) Math.exp(-elapsed / (double) TIME_CONSTANT_MILLIS));
        if (Math.abs(clamped - shown) < SNAP) {
            shown = clamped;
        }
        return shown;
    }
}
