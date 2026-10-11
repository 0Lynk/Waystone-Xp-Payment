package com.olynk.waystonexppayment.client;

/**
 * Geometry of the row context menu, as pure integers.
 *
 * <p>Kept out of the screen so the flip and clamp rules - the part that is easy to get subtly wrong and hard to
 * see in a running client - can be tested directly. All values are panel-relative: the caller draws inside a
 * {@code translate(leftPos, topPos, 0)} pose, so the panel origin is (0, 0) here.
 */
final class ContextMenuLayout {

    static final int ITEM_HEIGHT = 18;
    static final int ITEM_COUNT = 3;
    static final int PAD_Y = 3;
    static final int PAD_X = 6;
    static final int ICON_X = 6;
    static final int TEXT_X = 18;
    static final int MIN_WIDTH = 96;

    /** Constant whatever the items say: the height a finger learns once, and the flip maths stays stable. */
    static final int HEIGHT = PAD_Y * 2 + ITEM_COUNT * ITEM_HEIGHT;

    private static final int PANEL_RIGHT = 248;
    private static final int ROW_LEFT = 8;
    private static final int PANEL_BOTTOM = 240;

    private ContextMenuLayout() {
    }

    /** Width that fits the widest item label, never narrower than {@link #MIN_WIDTH}. */
    static int width(int widestItemText) {
        return Math.max(MIN_WIDTH, TEXT_X + widestItemText + PAD_X);
    }

    /**
     * Places a menu at a click point, flipping it when it would leave the panel.
     *
     * <p>The flip puts the whole menu on the far side of the cursor rather than sliding it along the edge, so
     * the cursor stays outside the menu on every edge and the row under it stays readable.
     */
    static Placement at(int anchorX, int anchorY, int width) {
        int x = anchorX;
        if (x + width > PANEL_RIGHT) {
            x = anchorX - width;
        }
        x = clamp(x, ROW_LEFT, PANEL_RIGHT - width);

        int y = anchorY;
        // One pixel of clearance: a border painted on the panel's own bottom edge reads as a clipped menu.
        if (y + HEIGHT > PANEL_BOTTOM - 1) {
            y = anchorY - HEIGHT;
        }
        y = clamp(y, 0, PANEL_BOTTOM - 1 - HEIGHT);

        return new Placement(x, y, width, HEIGHT);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    record Placement(int x, int y, int width, int height) {

        boolean contains(int pointX, int pointY) {
            return pointX >= x && pointX < x + width && pointY >= y && pointY < y + height;
        }

        /** Top of the item at {@code index}, padding included. */
        int itemTop(int index) {
            return y + PAD_Y + index * ITEM_HEIGHT;
        }

        /** Item under a point, or -1 for the padding and the edges. */
        int itemAt(int pointX, int pointY) {
            if (pointX < x + 1 || pointX >= x + width - 1) {
                return -1;
            }
            int index = (pointY - (y + PAD_Y)) / ITEM_HEIGHT;
            if (index < 0 || index >= ITEM_COUNT) {
                return -1;
            }
            // Java division truncates towards zero, so a point just above the first item divides to 0 rather
            // than a negative index; the explicit bounds check is what rejects it.
            int top = itemTop(index);
            return pointY >= top && pointY < top + ITEM_HEIGHT ? index : -1;
        }
    }
}
