package com.olynk.waystonexppayment.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * The two hint messages are the only thing standing between "the pool is far away" and "the button lies".
 *
 * <p>Two properties matter beyond the wire format: a query never carries more slots than the wire cap, and the
 * three answers stay distinguishable - in particular "unknown" must never be read as "no tank here", because that
 * is the case the remote-destination feature exists for.
 */
class XppTankHintQueryPayloadTest {

    private static final ResourceLocation OVERWORLD =
            ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

    @Test
    void deduplicatesSlots() {
        XppTankHintQueryPayload payload = XppTankHintQueryPayload.of(OVERWORLD,
                List.of(new BlockPos(1, 2, 3), new BlockPos(1, 2, 3), new BlockPos(4, 5, 6)));

        assertEquals(2, payload.tankSlots().size());
    }

    @Test
    void capsAtTheWireLimit() {
        List<BlockPos> slots = new ArrayList<>();
        for (int i = 0; i < XppTankHintQueryPayload.MAX_SLOTS + 10; i++) {
            slots.add(new BlockPos(i, 60, 0));
        }

        assertEquals(XppTankHintQueryPayload.MAX_SLOTS,
                XppTankHintQueryPayload.of(OVERWORLD, slots).tankSlots().size());
    }

    @Test
    void unknownIsNotTheSameAnswerAsNoTank() {
        BlockPos pos = new BlockPos(0, 64, 0);

        assertFalse(XppTankHintPayload.Hint.unknown(pos).hasTank());
        assertFalse(XppTankHintPayload.Hint.unknown(pos).known());
        assertTrue(XppTankHintPayload.Hint.none(pos).knownAbsent());
        assertFalse(XppTankHintPayload.Hint.none(pos).hasTank());
    }

    @Test
    void anEmptyTankStillCountsAsATank() {
        // The client must keep the payer (and the arithmetic) and let the split decide; reporting "no tank" here
        // would hide a tank that is merely empty at the moment the list was drawn.
        XppTankHintPayload.Hint empty = XppTankHintPayload.Hint.allowed(new BlockPos(0, 64, 0), 0, 1);

        assertTrue(empty.hasTank());
        assertTrue(empty.known());
    }
}
