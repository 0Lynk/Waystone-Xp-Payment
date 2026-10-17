package com.olynk.waystonexppayment.block.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The edit rule the state packet and the edit handler now share.
 *
 * <p>The reported bug: an operator opened a tank they do not own and every button was dead, even though the
 * server would have applied the edit. The two callers computed the rule separately and only one of them asked
 * about the operator exemption, so this rule has exactly one implementation and these are its three answers.
 */
class TankEditRightsTest {

    @Test
    void anOwnerOrTeamMateEditsWithoutBeingAnOperator() {
        assertTrue(XpPaymentTankBlockEntity.editAllowed(true, false));
    }

    @Test
    void anOperatorEditsATankThatDidNotListThem() {
        assertTrue(XpPaymentTankBlockEntity.editAllowed(false, true));
        assertTrue(XpPaymentTankBlockEntity.editAllowed(true, true));
    }

    @Test
    void aStrangerEditsNothing() {
        assertFalse(XpPaymentTankBlockEntity.editAllowed(false, false));
    }
}
