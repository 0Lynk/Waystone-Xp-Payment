package com.olynk.waystonexppayment.fluid;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Covers the one decision {@link FluidIds} makes on its own.
 *
 * <p>The rest of the class needs a fluid registry, so the restamping itself is exercised on a live client; what is
 * locked here is the branch that decides whether restamping happens at all.
 */
class FluidIdsTest {

    @Test
    void aFlowingFluidWithAKnownStillFormIsRestamped() {
        assertTrue(FluidIds.hasDistinctStillForm(true, true));
    }

    @Test
    void aSingleFormFluidIsLeftAlone() {
        assertFalse(FluidIds.hasDistinctStillForm(false, false));
    }

    @Test
    void aFlowingFluidWithoutAStillFormIsLeftAlone() {
        assertFalse(FluidIds.hasDistinctStillForm(true, false));
    }

    @Test
    void aNonFlowingFluidWithAStillFormIsLeftAlone() {
        assertFalse(FluidIds.hasDistinctStillForm(false, true));
    }
}
