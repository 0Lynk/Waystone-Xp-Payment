package com.olynk.waystonexppayment.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The one place every list payload now takes its limits from.
 *
 * <p>Both halves were bugs before: an uncapped count let a sender pre-size a list in the receiver's memory, and
 * {@code writeUtf} measures text in UTF-8 bytes while the old call sites counted characters - twenty-two Chinese
 * characters already overflowed a limit of 64. Truncating by code point is what keeps a multi-byte character from
 * being cut in half.
 */
class XppWireLimitsTest {

    @Test
    void capKeepsTheHead() {
        List<Integer> rows = new ArrayList<>();
        for (int i = 0; i < XppWireLimits.MAX_LIST + 5; i++) {
            rows.add(i);
        }

        List<Integer> capped = XppWireLimits.cap(rows);

        assertEquals(XppWireLimits.MAX_LIST, capped.size());
        assertEquals(0, capped.get(0));
        assertEquals(XppWireLimits.MAX_LIST - 1, capped.get(capped.size() - 1));
    }

    @Test
    void shortListsPassThroughUntouched() {
        List<String> rows = List.of("a", "b");

        assertEquals(rows, XppWireLimits.cap(rows));
    }

    @Test
    void textIsTruncatedOnByteNotCharacterBoundaries() {
        // U+4E2D is three bytes in UTF-8, so 64 bytes fit 21 of them with one byte left over - the 22nd must
        // not be emitted, and the 21st must survive whole.
        String chinese = "中".repeat(22);

        String capped = XppWireLimits.text(chinese);

        assertEquals("中".repeat(21), capped);
        assertTrue(capped.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= XppWireLimits.MAX_TEXT_BYTES);
    }

    @Test
    void textKeepsShortValuesAndNull() {
        assertEquals("hello", XppWireLimits.text("hello"));
        assertEquals("", XppWireLimits.text(null));
    }

    @Test
    void overLimitIsInclusiveOfTheCap() {
        assertFalse(XppWireLimits.overLimit(XppWireLimits.MAX_LIST));
        assertTrue(XppWireLimits.overLimit(XppWireLimits.MAX_LIST + 1));
    }
}
