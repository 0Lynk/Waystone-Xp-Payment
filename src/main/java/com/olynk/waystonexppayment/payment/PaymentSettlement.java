package com.olynk.waystonexppayment.payment;

/**
 * What a payment actually took, after the tank had its say.
 *
 * <p>{@link PaymentSplit} is a plan made before the teleport; this is the bill afterwards. They differ when the
 * tank empties between the two: Waystones validates a teleport and consumes it in one tick, but the tank is
 * resolved from the world both times, and the world can change in between. The tank's share is therefore counted
 * from what it really handed over, and the rest moves to the player instead of being silently forgiven.
 *
 * <p>{@link #xpStillOwed()} is zero in every case the player could cover. A non-zero value means the teleport
 * already happened and the player could not pay for it either; it exists to be logged, because a teleport that
 * cost nothing is a bug that must not pass unnoticed.
 */
public record PaymentSettlement(int xpFromTank, int xpFromPlayer, int xpStillOwed) {

    /**
     * @param agreed    the plan agreed in {@link PaymentSplit#compute}, never {@code null}
     * @param drainedMb fluid the tank actually gave back this tick (mB, >= 0)
     * @param playerXp  XP the player holds right now, at settlement time
     */
    public static PaymentSettlement settle(PaymentSplit agreed, int drainedMb, int playerXp) {
        // Floor, not round: the tank is credited only for whole XP it fully covered.
        int fromTank = Math.min(agreed.rate().xpOfMb(Math.max(0, drainedMb)), agreed.costXp());
        int remaining = agreed.costXp() - fromTank;
        int fromPlayer = Math.min(remaining, Math.max(0, playerXp));
        return new PaymentSettlement(fromTank, fromPlayer, remaining - fromPlayer);
    }

    /** Whether the plan was met in full - the common case, and the only one that needs no explaining in logs. */
    public boolean isComplete() {
        return xpStillOwed == 0;
    }
}
