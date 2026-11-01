package com.olynk.waystonexppayment.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TankLightTest {

    private static final int DIM = 0x0010_0000;
    private static final int FULL_BRIGHT = 0x00F0_0000;

    @Test
    void aFluidThatDeclaresNoLightUsesTheRoomItSitsIn() {
        assertEquals(DIM, TankLight.packed(DIM, 0, FULL_BRIGHT));
    }

    @Test
    void aGlowingFluidIgnoresTheRoom() {
        assertEquals(FULL_BRIGHT, TankLight.packed(DIM, 15, FULL_BRIGHT));
        assertEquals(FULL_BRIGHT, TankLight.packed(DIM, 1, FULL_BRIGHT));
    }

    @Test
    void aBrightRoomKeepsItsLightForADarkFluid() {
        assertEquals(FULL_BRIGHT, TankLight.packed(FULL_BRIGHT, 0, FULL_BRIGHT));
    }
}
