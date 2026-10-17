package com.olynk.waystonexppayment.event;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * When looking for a payer tank may still spend the attempt's chunk-load permit.
 *
 * <p>What this guards: a resident chunk can never be loaded, so a tank search that already probed it has nothing
 * left to learn - yet asking the budget anyway consumed the permit. With the source waystone resident by
 * definition and often without a tank, it took the permit the destination end needed, and the first click fell
 * back to the player's own XP.
 */
class XppTeleportHandlerTest {

    @Test
    void aResidentChunkWithoutATankIsNotWorthALoad() {
        assertFalse(XppTeleportHandler.shouldLoadForTank(false, true));
    }

    @Test
    void aMissingTankInAForeignChunkNeedsTheLoad() {
        assertTrue(XppTeleportHandler.shouldLoadForTank(false, false));
    }

    @Test
    void aTankAlreadyInHandNeedsNoLoad() {
        assertFalse(XppTeleportHandler.shouldLoadForTank(true, false));
    }

    @Test
    void aTankInAResidentChunkNeedsNoLoad() {
        assertFalse(XppTeleportHandler.shouldLoadForTank(true, true));
    }
}
