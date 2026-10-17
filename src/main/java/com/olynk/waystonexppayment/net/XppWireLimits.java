package com.olynk.waystonexppayment.net;

import java.util.List;

/**
 * Wire limits shared by every payload of this mod.
 *
 * <p>WHY one class: the same three numbers had been copied into four payloads, and the copy that carried the
 * access list was the only one without a write-side cap - a reader that refuses 257 rows plus a writer that
 * happily sends them is a closed screen nobody can reopen. A limit that exists once cannot go missing in one
 * of the copies.
 */
public final class XppWireLimits {

    /** Rows one list-carrying payload may send or receive. */
    public static final int MAX_LIST = 256;

    /** Slots one tank-hint payload may carry. */
    public static final int MAX_HINTS = 32;

    /** UTF-8 bytes one label may occupy on the wire. */
    public static final int MAX_TEXT_BYTES = 64;

    private XppWireLimits() {
    }

    /** Drops rows past {@link #MAX_LIST}; the reader on the other side refuses the packet otherwise. */
    public static <T> List<T> cap(List<T> rows) {
        return rows.size() <= MAX_LIST ? rows : List.copyOf(rows.subList(0, MAX_LIST));
    }

    /**
     * Truncates text to {@link #MAX_TEXT_BYTES} without splitting a character.
     *
     * <p>{@code FriendlyByteBuf.writeUtf(value, maxLength)} measures that limit in <em>UTF-8 bytes</em>, not in
     * characters, so 22 Chinese characters already exceed 64 - truncating by {@code String.length()} would leave
     * the encode to throw on the sending side instead of shortening the text.
     */
    public static String text(String value) {
        if (value == null) {
            return "";
        }
        int bytes = 0;
        for (int index = 0; index < value.length(); ) {
            int codePoint = value.codePointAt(index);
            int size = utf8Length(codePoint);
            if (bytes + size > MAX_TEXT_BYTES) {
                return value.substring(0, index);
            }
            bytes += size;
            index += Character.charCount(codePoint);
        }
        return value;
    }

    /** True when a count read off the wire is outside what a list-carrying payload may carry. */
    public static boolean overLimit(int count) {
        return count < 0 || count > MAX_LIST;
    }

    private static int utf8Length(int codePoint) {
        if (codePoint < 0x80) {
            return 1;
        }
        if (codePoint < 0x800) {
            return 2;
        }
        return codePoint < 0x10000 ? 3 : 4;
    }
}
