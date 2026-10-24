package com.olynk.waystonexppayment.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** The button's grey-ownership rule: grey only what we can pay for, and undo only our own grey. */
class PaymentGreyStateTest {

    private final PaymentGreyState state = new PaymentGreyState();

    @Test
    void turnsOffAButtonTheTankCanNotPay() {
        assertEquals(Boolean.FALSE, state.nextActive(false, true));
    }

    @Test
    void staysQuietWhileNothingChanges() {
        assertNull(state.nextActive(true, true), "a payable, already clickable entry is none of our business");
        assertEquals(Boolean.FALSE, state.nextActive(false, true), "the first unaffordable frame is ours");
        assertNull(state.nextActive(false, false), "every further unaffordable frame changes nothing");
    }

    @Test
    void lightsTheButtonBackWhenTheTankRecovers() {
        assertEquals(Boolean.FALSE, state.nextActive(false, true));
        assertEquals(Boolean.TRUE, state.nextActive(true, false), "the grey was ours, so the recovery is too");
        assertNull(state.nextActive(true, true), "and then it is Waystones' button again");
    }

    @Test
    void lightsTheButtonBackWhenTheListWasOpenedShort() {
        assertNull(state.nextActive(false, false), "opened while short: Waystones already applied our rule");
        assertNull(state.nextActive(false, false), "still short, nothing to do");
        assertEquals(Boolean.TRUE, state.nextActive(true, false), "the refill is the whole point of this class");
    }

    @Test
    void neverLightsAButtonThatWasGreyWhileItCouldPay() {
        assertNull(state.nextActive(true, false), "grey while payable is a reason of Waystones' own");
        assertNull(state.nextActive(true, false), "affordability alone must not turn it clickable");
        assertNull(state.nextActive(false, false), "and it stays that way even when the tank runs dry");
    }
}
