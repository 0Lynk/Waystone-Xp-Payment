package com.olynk.waystonexppayment.block.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;

/**
 * Which pool number the client shows, and when "no number yet" is the honest answer.
 *
 * <p>Both halves used to be wrong in ways only a loaded world revealed: preferring the locally computed pool
 * showed a stale value after the tank was drained, and treating any load as if the sync tag had arrived meant a
 * tank whose data had never been sent looked like an empty one.
 */
class TankPoolViewTest {

    @Test
    void theServerNumberWinsWhenItArrived() {
        assertEquals(100, XpPaymentTankBlockEntity.pickPool(100, 50));
        assertEquals(0, XpPaymentTankBlockEntity.pickPool(0, 50), "a drained tank must not fall back to the old number");
    }

    @Test
    void theLocalNumberIsUsedUntilTheServerSpeaks() {
        assertEquals(50, XpPaymentTankBlockEntity.pickPool(-1, 50));
    }

    @Test
    void noReadingAtAllIsZeroRatherThanNegative() {
        assertEquals(0, XpPaymentTankBlockEntity.pickPool(-1, -1));
    }

    @Test
    void hasSyncedDataIsFalseWithoutSyncKey() {
        assertFalse(XpPaymentTankBlockEntity.carriesSyncedPool(new CompoundTag()));
        assertTrue(XpPaymentTankBlockEntity.carriesSyncedPool(syncTag()));
    }

    private static CompoundTag syncTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("XppPoolXp", 42);
        return tag;
    }
}
