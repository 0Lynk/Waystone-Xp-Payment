package com.olynk.waystonexppayment.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

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
}
