package com.olynk.waystonexppayment.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/**
 * The hint answer is the client's only source for a tank it cannot see, so its three states have to survive the
 * wire and mean what the server meant.
 *
 * <p>The state that matters most is {@code UNKNOWN}: it is the one a two-valued answer cannot express, and reading
 * it as either "no tank" or "denied" is exactly the bug this round exists to fix.
 */
class XppTankHintPayloadTest {

    private static final BlockPos POS = new BlockPos(3, 64, -7);

    @Test
    void accessRoundTripsAllThreeStates() {
        for (XppTankHintPayload.Access access : XppTankHintPayload.Access.values()) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

            XppTankHintPayload.writeAccess(buffer, access);

            assertEquals(access, XppTankHintPayload.readAccess(buffer));
        }
    }

    @Test
    void decoderRejectsAccessOutOfRange() {
        // A byte no version of this mod wrote means the streams are out of step; decoding it as some state would
        // silently turn a protocol mismatch into a payment decision.
        assertThrows(DecoderException.class, () -> readRaw(3));
        assertThrows(DecoderException.class, () -> readRaw(-1));
    }

    private static XppTankHintPayload.Access readRaw(int raw) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeByte(raw);
        return XppTankHintPayload.readAccess(buffer);
    }

    @Test
    void deniedHintCarriesNoPool() {
        XppTankHintPayload.Hint denied = XppTankHintPayload.Hint.denied(POS);

        assertTrue(denied.hasTank(), "the slot is known to hold a tank, so the client can stop asking");
        assertEquals(0, denied.poolXp(), "a denied viewer must not learn how much the owner has stored");
        assertEquals(XppTankHintPayload.Access.DENIED, denied.access());
        assertFalse(denied.mayPay());
    }

    @Test
    void noTankAndUnknownCarryAccessUnknown() {
        assertEquals(XppTankHintPayload.Access.UNKNOWN, XppTankHintPayload.Hint.none(POS).access());
        assertEquals(XppTankHintPayload.Access.UNKNOWN, XppTankHintPayload.Hint.unknown(POS).access());
        assertTrue(XppTankHintPayload.Hint.none(POS).knownAbsent());
        assertFalse(XppTankHintPayload.Hint.unknown(POS).known());
    }

    @Test
    void noTankIsNotAPayer() {
        assertFalse(XppTankHintPayload.Hint.none(POS).mayPay());
    }

    @Test
    void unknownAccessStaysPermissive() {
        XppTankHintPayload.Hint far = new XppTankHintPayload.Hint(POS, 120, 1, XppTankHintPayload.Access.UNKNOWN);

        assertTrue(far.mayPay(), "a far-away tank the server could not check must stay clickable");
    }

    @Test
    void unknownPoolStaysPermissive() {
        // No recorded pool and no loaded chunk: nothing is known, not even that a tank is there. Greying the
        // button here would rebuild the limitation the feature was built to remove.
        assertTrue(XppTankHintPayload.Hint.unknown(POS).mayPay());
    }

    @Test
    void allowedWithPoolIsAPayer() {
        assertTrue(XppTankHintPayload.Hint.allowed(POS, 120, 2).mayPay());
    }

    @Test
    void emptyButAllowedTankIsStillAPayer() {
        // An empty tank is a fact of the moment, not a verdict: the split decides, and the tank may be filled
        // before the button is clicked again.
        assertTrue(XppTankHintPayload.Hint.allowed(POS, 0, 1).mayPay());
    }
}
