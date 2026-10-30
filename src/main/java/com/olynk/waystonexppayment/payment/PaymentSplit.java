package com.olynk.waystonexppayment.payment;

import com.olynk.waystonexppayment.fluid.XpRate;

/**
 * Pure (Minecraft-free) split of a teleport XP cost between a shared fluid pool and the player's own XP.
 *
 * <p>Rules (user spec m00435 #7): the pool pays first; the remainder is paid by the player; if the pool and the
 * player together cannot cover the cost, the teleport is rejected <em>without</em> any deduction
 * ({@link #affordable()} is false and callers must not consume).
 *
 * <p>The rate travels with the plan on purpose: the tank is drained later, from a world that may have changed,
 * and settling the bill with a freshly resolved rate would charge a price nobody agreed to.
 */
public record PaymentSplit(int costXp, int xpFromFluid, XpRate rate, int fluidUsedMb, int xpFromPlayer,
                           boolean affordable) {

    /**
     * @param costXp    the teleport's XP cost as resolved by Waystones (>= 0)
     * @param poolXp    XP value currently available in the device tank (>= 0)
     * @param rate      conversion rate of the tank fluid, never {@code null}
     * @param playerXp  total XP currently held by the player (>= 0)
     */
    public static PaymentSplit compute(int costXp, int poolXp, XpRate rate, int playerXp) {
        if (rate == null) {
            throw new IllegalArgumentException("rate must be known, was null");
        }
        int cost = Math.max(0, costXp);
        int pool = Math.max(0, poolXp);
        int held = Math.max(0, playerXp);

        int fromFluid = Math.min(cost, pool);
        int fromPlayer = cost - fromFluid;
        boolean affordable = fromPlayer <= held;
        return new PaymentSplit(cost, fromFluid, rate, rate.mbForXp(fromFluid), fromPlayer, affordable);
    }

    /** Nothing to pay at all - callers should leave the teleport untouched instead of installing this split. */
    public boolean isNoop() {
        return costXp <= 0;
    }
}
