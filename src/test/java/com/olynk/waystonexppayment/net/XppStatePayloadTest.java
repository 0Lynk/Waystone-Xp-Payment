package com.olynk.waystonexppayment.net;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.handler.codec.DecoderException;
import org.junit.jupiter.api.Test;

/**
 * Guards the one wire-side allocation that trusts the sender: a row count read as a VarInt.
 *
 * <p>Without the cap a client could ask the server to pre-size a list with any number it likes (the tank screen
 * shows a handful of rows), so a big count is malformed or hostile and the packet has to be refused.
 */
class XppStatePayloadTest {

    @Test
    void rowCountOutsideTheWireCapIsRefused() {
        assertThrows(DecoderException.class, () -> XppStatePayload.requireEntryCount(-1));
        assertThrows(DecoderException.class, () -> XppStatePayload.requireEntryCount(XppStatePayload.MAX_ENTRIES + 1));
        assertThrows(DecoderException.class, () -> XppStatePayload.requireEntryCount(Integer.MAX_VALUE));
    }

    @Test
    void rowCountInsideTheWireCapIsAccepted() {
        assertDoesNotThrow(() -> XppStatePayload.requireEntryCount(0));
        assertDoesNotThrow(() -> XppStatePayload.requireEntryCount(1));
        assertDoesNotThrow(() -> XppStatePayload.requireEntryCount(XppStatePayload.MAX_ENTRIES));
    }
}
