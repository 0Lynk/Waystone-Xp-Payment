package com.olynk.waystonexppayment.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

/**
 * The slot a tank under a waystone can occupy, including the case where the waystone's shape cannot be read.
 *
 * <p>What this guards: a chunk that is not loaded has no block states, and the previous rule read that as "single
 * block waystone" and looked for the tank one block too high - its own lower half. Waystones recorded on the upper
 * half then reported an empty pool while the real tank stood right below it.
 */
class TankPlacementTest {

    /** A waystone recorded on its upper half: both "one below" and "two below" are plausible for a tank. */
    private static final BlockPos WAYSTONE = new BlockPos(1186, 66, 1212);

    @Test
    void recordedUpperHalfPutsTheTankTwoBlocksDown() {
        List<BlockPos> candidates =
                TankPlacement.candidateTankPositions(WAYSTONE, TankPlacement.RecordedHalf.UPPER_HALF);

        assertEquals(List.of(new BlockPos(1186, 64, 1212)), candidates);
        assertFalse(candidates.contains(WAYSTONE.below()),
                "the waystone's own lower half is not a tank slot - that answer is the bug being fixed");
    }

    @Test
    void recordedLowerHalfPutsTheTankDirectlyBelow() {
        List<BlockPos> candidates =
                TankPlacement.candidateTankPositions(WAYSTONE, TankPlacement.RecordedHalf.LOWER_OR_SINGLE);

        assertEquals(List.of(WAYSTONE.below()), candidates);
    }

    @Test
    void singleBlockSourcePutsTheTankDirectlyBelow() {
        // Same enum value as the lower half on purpose: a warp plate has nothing under it, so one block down is
        // the slot either way. Keeping them one case is what stops the two shapes from drifting apart.
        List<BlockPos> candidates =
                TankPlacement.candidateTankPositions(WAYSTONE, TankPlacement.RecordedHalf.LOWER_OR_SINGLE);

        assertEquals(1, candidates.size(), "a single-block source has exactly one slot, not two");
        assertEquals(WAYSTONE.below(), candidates.get(0));
    }

    @Test
    void unreadableWaystoneKeepsBothSlotsInPlay() {
        List<BlockPos> candidates =
                TankPlacement.candidateTankPositions(WAYSTONE, TankPlacement.RecordedHalf.UNKNOWN);

        assertEquals(List.of(WAYSTONE.below(), WAYSTONE.below().below()), candidates,
                "both answers, in preference order: one block down is the common case");
    }

    @Test
    void unreadableWaystoneNeverRepeatsASlot() {
        List<BlockPos> candidates =
                TankPlacement.candidateTankPositions(WAYSTONE, TankPlacement.RecordedHalf.UNKNOWN);

        assertEquals(candidates.size(), new HashSet<>(candidates).size(),
                "a repeated slot would spend two chunk-load budget entries on the same chunk");
    }

    @Test
    void unreadableWaystoneDropsNoCandidateAnswer() {
        // The invariant behind the fix: when the shape is unknown, neither of the two known answers may be lost.
        for (BlockPos waystone : List.of(WAYSTONE, new BlockPos(564, 63, -673), new BlockPos(-3, 5, 7))) {
            List<BlockPos> unknown =
                    TankPlacement.candidateTankPositions(waystone, TankPlacement.RecordedHalf.UNKNOWN);

            assertTrue(unknown.containsAll(
                            TankPlacement.candidateTankPositions(waystone, TankPlacement.RecordedHalf.UPPER_HALF)),
                    "the two-blocks-down answer is missing for " + waystone.toShortString());
            assertTrue(unknown.containsAll(TankPlacement.candidateTankPositions(waystone,
                            TankPlacement.RecordedHalf.LOWER_OR_SINGLE)),
                    "the one-block-down answer is missing for " + waystone.toShortString());
        }
    }
}
