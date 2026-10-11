package com.olynk.waystonexppayment.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.olynk.waystonexppayment.client.ContextMenuLayout.Placement;

/** Flip, clamp and hit-testing rules of the row context menu. */
class ContextMenuLayoutTest {

    /** The width a CJK label bottoms out at, and the height every menu has. */
    private static final int CJK_WIDTH = 96;

    @Test
    void widthNeverGoesBelowTheMinimum() {
        assertEquals(96, ContextMenuLayout.width(0));
        assertEquals(96, ContextMenuLayout.width(50), "18 + 50 + 6 = 74 still bottoms out at the minimum");
        assertEquals(104, ContextMenuLayout.width(80));
    }

    @Test
    void heightIsConstant() {
        // The three items plus the padding, and nothing else: the flip maths depends on this never varying.
        assertEquals(3 + 3 * 18 + 3, ContextMenuLayout.HEIGHT);
        assertEquals(60, ContextMenuLayout.HEIGHT);
    }

    @Test
    void menuOpensWhereItFits() {
        Placement top = ContextMenuLayout.at(140, 124, CJK_WIDTH);

        assertEquals(140, top.x());
        assertEquals(124, top.y());
    }

    @Test
    void rowFourFlipsUp() {
        // 187 + 60 would run 7px past the panel bottom, so the whole menu goes above the cursor.
        Placement flipped = ContextMenuLayout.at(140, 187, CJK_WIDTH);

        assertEquals(140, flipped.x());
        assertEquals(127, flipped.y());
    }

    @Test
    void rowFiveFlipsUp() {
        Placement flipped = ContextMenuLayout.at(140, 208, CJK_WIDTH);

        assertEquals(140, flipped.x());
        assertEquals(148, flipped.y());
    }

    @Test
    void nearTheRightEdgeFlipsLeft() {
        Placement flipped = ContextMenuLayout.at(230, 187, CJK_WIDTH);

        assertEquals(134, flipped.x());
        assertEquals(127, flipped.y());
    }

    @Test
    void aWideMenuAtTheRightEdgeIsClampedToTheRowLeft() {
        // 140 - 126 = 14, which is above the row's left edge: the clamp takes over.
        Placement clamped = ContextMenuLayout.at(140, 208, 126);

        assertEquals(14, clamped.x());
        assertEquals(148, clamped.y());
    }

    @Test
    void extremeCoordinatesAreClampedIntoThePanel() {
        Placement topLeft = ContextMenuLayout.at(0, 0, CJK_WIDTH);
        assertEquals(8, topLeft.x(), "never further left than the row area");
        assertEquals(0, topLeft.y());

        Placement bottomRight = ContextMenuLayout.at(240, 239, CJK_WIDTH);
        assertEquals(144, bottomRight.x());
        assertEquals(179, bottomRight.y(), "one pixel of clearance above the panel's bottom edge");
    }

    @Test
    void itemsDivideTheBodyAndThePaddingBelongsToNone() {
        Placement menu = ContextMenuLayout.at(140, 124, CJK_WIDTH);

        assertEquals(127, menu.itemTop(0));
        assertEquals(163, menu.itemTop(2));
        assertEquals(0, menu.itemAt(150, 127));
        assertEquals(1, menu.itemAt(150, 145));
        assertEquals(2, menu.itemAt(150, 163));
        assertEquals(-1, menu.itemAt(150, 126), "the top padding is not the first item");
        assertEquals(-1, menu.itemAt(150, 181), "the bottom padding is not the third item");
    }

    @Test
    void itemHitTestingStopsAtTheBorders() {
        Placement menu = ContextMenuLayout.at(140, 124, CJK_WIDTH);

        assertEquals(-1, menu.itemAt(140, 130), "the 1px left border is not an item");
        assertEquals(-1, menu.itemAt(235, 130), "the 1px right border is not an item");
        assertEquals(0, menu.itemAt(141, 130));
        assertEquals(0, menu.itemAt(234, 130));
    }

    @Test
    void containsCoversTheWholeBody() {
        Placement menu = ContextMenuLayout.at(140, 124, CJK_WIDTH);

        assertTrue(menu.contains(140, 124));
        assertTrue(menu.contains(235, 183));
        assertFalse(menu.contains(236, 124), "the right column is outside");
        assertFalse(menu.contains(140, 184), "the bottom row is outside");
        assertFalse(menu.contains(139, 124));
    }
}
