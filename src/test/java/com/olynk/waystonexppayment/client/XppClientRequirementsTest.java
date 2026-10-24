package com.olynk.waystonexppayment.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.olynk.waystonexppayment.net.XppTankHintPayload;
import com.olynk.waystonexppayment.payment.TankPlacement;

import net.minecraft.core.BlockPos;

/**
 * Which of the two possible tank slots the client offers as the payer.
 *
 * <p>What this guards: with an unloaded waystone the tank can be in either slot, so the entry may only stay grey
 * when neither slot can pay - and a slot this client remembers seeing must not be dropped for a hint about the
 * other one.
 */
class XppClientRequirementsTest {

    private static final BlockPos WAYSTONE = new BlockPos(1186, 66, 1212);

    /** The real candidate list for an unreadable waystone: one below, then two below. */
    private static final List<BlockPos> CANDIDATES =
            TankPlacement.candidateTankPositions(WAYSTONE, TankPlacement.RecordedHalf.UNKNOWN);

    private static final BlockPos NEAR = CANDIDATES.get(0);
    private static final BlockPos FAR = CANDIDATES.get(1);

    private static final UUID SOURCE_UID = UUID.fromString("a1e73d9d-9163-4ca0-8497-1301eed23a9a");
    private static final UUID TARGET_UID = UUID.fromString("0f3f7c2b-5b3d-4a1e-9c88-7d2b1f4e6a10");

    @Test
    void nothingKnownKeepsTheEntryGated() {
        assertNull(XppClientRequirements.selectPayerSlot(CANDIDATES, slot -> false, slot -> null),
                "no memory and no answer: the entry must not light up yet");
    }

    @Test
    void aRememberedTankBeatsAHintOnTheOtherSlot() {
        assertEquals(FAR, XppClientRequirements.selectPayerSlot(CANDIDATES, FAR::equals,
                        slot -> NEAR.equals(slot) ? XppTankHintPayload.Hint.allowed(NEAR, 900, 20) : null),
                "what this client saw is ground truth; a cached answer about the other slot cannot outrank it");
    }

    @Test
    void anUnansweredPoolOnTheFirstSlotAlreadyLightsTheButton() {
        assertEquals(NEAR, XppClientRequirements.selectPayerSlot(CANDIDATES, slot -> false,
                slot -> NEAR.equals(slot) ? XppTankHintPayload.Hint.unknown(NEAR) : null));
    }

    @Test
    void aTankOnTheSecondSlotIsFoundWhenTheFirstSlotHasNone() {
        // The reported case: the waystone is recorded on its upper half, so the lower candidate is the waystone's
        // own lower half - no tank there - and the tank is two blocks down.
        assertEquals(FAR, XppClientRequirements.selectPayerSlot(CANDIDATES, slot -> false,
                slot -> FAR.equals(slot) ? XppTankHintPayload.Hint.unknown(FAR) : XppTankHintPayload.Hint.none(slot)));
    }

    @Test
    void anEmptyTankOnOneSlotDoesNotShadowTheOther() {
        assertEquals(FAR, XppClientRequirements.selectPayerSlot(CANDIDATES, slot -> false,
                slot -> NEAR.equals(slot) ? XppTankHintPayload.Hint.allowed(NEAR, 0, 20)
                        : XppTankHintPayload.Hint.unknown(FAR)));
    }

    @Test
    void aDeniedTankIsNotAPayer() {
        assertNull(XppClientRequirements.selectPayerSlot(CANDIDATES, slot -> false,
                slot -> NEAR.equals(slot) ? XppTankHintPayload.Hint.denied(NEAR) : null),
                "the player may not spend from that tank, whatever it holds");
    }

    @Test
    void noTankAtEitherSlotIsNotAPayer() {
        assertNull(XppClientRequirements.selectPayerSlot(CANDIDATES, slot -> false,
                XppTankHintPayload.Hint::none));
    }

    @Test
    void theWaystoneYouStandOnIsNotATripToPrice() {
        assertTrue(XppClientRequirements.isSameWaystone(SOURCE_UID, SOURCE_UID),
                "Waystones greys that row itself; a payment preview would light it up on the next refill");
    }

    @Test
    void aDifferentDestinationKeepsItsPreview() {
        assertFalse(XppClientRequirements.isSameWaystone(SOURCE_UID, TARGET_UID));
    }

    @Test
    void anUnknownSourceIsNotASelfTrip() {
        assertFalse(XppClientRequirements.isSameWaystone(null, TARGET_UID),
                "a warp item carries no source waystone, and every entry still needs its preview");
    }
}
