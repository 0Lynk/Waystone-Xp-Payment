package com.olynk.waystonexppayment.payment;

import com.olynk.waystonexppayment.fluid.XpRate;

/**
 * What one teleport would cost a player whose tank holds a readable pool, in every unit the answer is asked in.
 *
 * <p>The payment path used to answer "can this trip be paid" in one place and "how much is in that tank" in
 * another, which left a tank of a cheap fluid looking identical to an empty one: the screen showed 1000 mB and the
 * entry went grey, with no number saying the fluid was worth 10 XP rather than 1000. One quote, computed once,
 * feeds the decision, the tooltip, the tank screen and the log.
 *
 * <p>Minecraft-free on purpose: an operator reporting "my tank is full and it still will not pay" is a statement
 * about these six numbers, and it has to be reproducible without a game running.
 */
public record PoolQuote(int costXp, int poolXp, int xpFromTank, int xpFromPlayer, int tankMb, int missingXp,
                        int missingMb) {

    /**
     * @param costXp   the teleport's XP cost as resolved by Waystones (>= 0)
     * @param poolXp   XP value currently available in the tank (>= 0)
     * @param rate     conversion rate of the tank fluid, never {@code null}
     * @param playerXp total XP currently held by the player (>= 0)
     */
    public static PoolQuote of(int costXp, int poolXp, XpRate rate, int playerXp) {
        if (rate == null) {
            throw new IllegalArgumentException("rate must be known, was null");
        }
        int cost = Math.max(0, costXp);
        int pool = Math.max(0, poolXp);
        int held = Math.max(0, playerXp);

        int fromTank = Math.min(cost, pool);
        int fromPlayer = cost - fromTank;
        int missing = Math.max(0, fromPlayer - held);
        return new PoolQuote(cost, pool, fromTank, fromPlayer, rate.mbForXp(fromTank), missing, rate.mbForXp(missing));
    }

    /** Whether the tank and the player together can cover the cost. */
    public boolean affordable() {
        return missingXp == 0;
    }

    /** Whether the tank alone covers the whole cost, which is the only case where the player pays nothing. */
    public boolean tankCoversAll() {
        return xpFromTank == costXp;
    }
}
