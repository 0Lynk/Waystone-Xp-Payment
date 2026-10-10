package com.olynk.waystonexppayment.payment;

/**
 * Pure (Minecraft-free) split of a teleport XP cost between a shared fluid pool and the player's own XP.
 *
 * <p>Rules (user spec m00435 #7): the pool pays first; the remainder is paid by the player; if the pool and the
 * player together cannot cover the cost, the teleport is rejected <em>without</em> any deduction
 * ({@link #affordable()} is false and callers must not consume).
 */
public record PaymentSplit(int costXp, int xpFromFluid, int mbPerXp, int fluidUsedMb, int xpFromPlayer,
                           boolean affordable) {

    /**
     * @param costXp    the teleport's XP cost as resolved by Waystones (>= 0)
     * @param poolXp    XP value currently available in the device tank (>= 0)
     * @param mbPerXp   conversion rate of the tank fluid: mB needed for 1 XP (>= 1)
     * @param playerXp  total XP currently held by the player (>= 0)
     */
    public static PaymentSplit compute(int costXp, int poolXp, int mbPerXp, int playerXp) {
        if (mbPerXp < 1) {
            throw new IllegalArgumentException("mbPerXp must be >= 1, was " + mbPerXp);
        }
        int cost = Math.max(0, costXp);
        int pool = Math.max(0, poolXp);
        int held = Math.max(0, playerXp);

        int fromFluid = Math.min(cost, pool);
        int fromPlayer = cost - fromFluid;
        boolean affordable = fromPlayer <= held;
        long usedMb = (long) fromFluid * mbPerXp;
        return new PaymentSplit(cost, fromFluid, mbPerXp, (int) usedMb, fromPlayer, affordable);
    }

    /** Nothing to pay at all - callers should leave the teleport untouched instead of installing this split. */
    public boolean isNoop() {
        return costXp <= 0;
    }
}
