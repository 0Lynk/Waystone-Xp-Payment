package com.olynk.waystonexppayment.client;

/**
 * The selection button's "who decided this is grey" state.
 *
 * <p>Waystones sets {@code active} once, when the entry is built, so a preview that could only turn it off left the
 * entry unclickable for as long as the screen stays open: one unaffordable frame - the pump emptying the tank while
 * the list is open - outlived the refill, and only reopening the list rebuilt the button. Reported in game as
 * "refilled the tank, the entry is still grey".
 *
 * <p>The answer is three-valued: {@code null} means "leave the button alone". That happens for an entry that was
 * already grey while this trip was affordable - grey for a reason of Waystones' own (you are standing on it), which
 * affordability must never override.
 *
 * <p>Mutable on purpose: the decision has to survive between frames of one screen, and the unit test has to be able
 * to walk it through take-then-give-back without a Minecraft client.
 */
public final class PaymentGreyState {

    private Boolean ours;
    private boolean greyedByPayment;

    /**
     * @param affordable the preview says this trip can be paid right now
     * @param activeNow  the button's current {@code active} flag
     * @return the value the button should be set to, or {@code null} to leave it untouched
     */
    public Boolean nextActive(boolean affordable, boolean activeNow) {
        if (ours == null) {
            // Affordable yet already grey cannot be our rule, because our rule only ever greys what it cannot pay
            // for. Everything else is ours to restore, including a list opened while the tank was already short -
            // that grey was this same rule, applied by Waystones instead of by us.
            ours = !affordable || activeNow;
        }
        if (!ours) {
            return null;
        }
        if (!affordable) {
            if (!activeNow) {
                greyedByPayment = true;
                return null;
            }
            greyedByPayment = true;
            return Boolean.FALSE;
        }
        if (!greyedByPayment) {
            return null;
        }
        greyedByPayment = false;
        return activeNow ? null : Boolean.TRUE;
    }
}
