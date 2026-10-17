package com.olynk.waystonexppayment.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/**
 * The access-list mode travels as its own field now, not as text inside {@code label}.
 *
 * <p>Why this deserves a test at all: the mode is a permission rule, and it used to be derived from a display
 * string. A reworded or translated label would have flipped white-list and black-list without any compile error,
 * so the property "the label cannot decide the mode" is worth pinning in a machine-checked place.
 */
class XppAccessPayloadTest {

    @Test
    void setModeCarriesDedicatedField() {
        for (XppAccessPayload.Mode mode : XppAccessPayload.Mode.values()) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

            XppAccessPayload.writeMode(buffer, mode);

            assertEquals(mode, XppAccessPayload.readMode(buffer));
        }
    }

    @Test
    void absentModeRoundTripsAsNull() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        XppAccessPayload.writeMode(buffer, null);

        assertNull(XppAccessPayload.readMode(buffer), "actions that carry no mode must stay distinguishable");
    }

    @Test
    void modeFieldIgnoresLabel() {
        XppAccessPayload payload = new XppAccessPayload(BlockPos.ZERO, XppAccessPayload.Action.SET_MODE,
                XppAccessPayload.Mode.WHITELIST, null, "blacklist", 0L);

        assertEquals(XppAccessPayload.Mode.WHITELIST, payload.mode(),
                "the label is log text; the mode is the field, and nothing may derive one from the other");
    }
}
