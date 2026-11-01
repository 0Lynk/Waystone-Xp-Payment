package com.olynk.waystonexppayment.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Guards the winding and sprite orientation of the fluid quads.
 *
 * <p>Both went wrong once: the quads were wound inwards, so a player outside the tank saw the back of the sprite and
 * the inside saw the front. Nothing in the game complains about that, it just looks mirrored, so it needs a test.
 */
class TankFacesTest {

    private static final float NEAR = 0.5F;
    private static final float FAR = 1.5F;
    private static final float TOP = 2.5F;

    @Test
    void everyFaceIsWoundCounterClockwiseSeenFromOutside() {
        for (TankFaces.Side side : TankFaces.Side.values()) {
            TankFaces.Quad quad = quad(side);
            TankFaces.Corner[] c = quad.corners();
            float ax = c[1].x() - c[0].x();
            float ay = c[1].y() - c[0].y();
            float az = c[1].z() - c[0].z();
            float bx = c[2].x() - c[1].x();
            float by = c[2].y() - c[1].y();
            float bz = c[2].z() - c[1].z();
            float nx = ay * bz - az * by;
            float ny = az * bx - ax * bz;
            float nz = ax * by - ay * bx;
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            assertTrue(length > 0F, side + " has a degenerate triangle");
            assertEquals(quad.normalX(), nx / length, 1e-4F, side + " normal x");
            assertEquals(quad.normalY(), ny / length, 1e-4F, side + " normal y");
            assertEquals(quad.normalZ(), nz / length, 1e-4F, side + " normal z");
        }
    }

    @Test
    void theSpriteRunsLeftToRightForSomeoneStandingOutside() {
        for (TankFaces.Side side : TankFaces.Side.values()) {
            TankFaces.Corner[] c = quad(side).corners();
            float[] right = screenRight(side);
            float alongRight = (c[1].x() - c[0].x()) * right[0] + (c[1].y() - c[0].y()) * right[1]
                    + (c[1].z() - c[0].z()) * right[2];
            assertTrue(alongRight > 0F, side + " runs the sprite backwards");
        }
    }

    @Test
    void theSidesStartAtTheirBottomEdge() {
        for (TankFaces.Side side : TankFaces.Side.values()) {
            if (side == TankFaces.Side.TOP) {
                continue;
            }
            TankFaces.Corner[] c = quad(side).corners();
            assertEquals(NEAR, c[0].y(), 1e-4F, side + " first corner is not on the bottom");
            assertEquals(NEAR, c[1].y(), 1e-4F, side + " second corner is not on the bottom");
            assertEquals(TOP, c[2].y(), 1e-4F, side + " third corner is not on the top");
            assertEquals(TOP, c[3].y(), 1e-4F, side + " fourth corner is not on the top");
        }
    }

    @Test
    void theTopFaceRunsItsSpriteFromNorthToSouth() {
        TankFaces.Corner[] c = quad(TankFaces.Side.TOP).corners();
        assertEquals(FAR, c[0].z(), 1e-4F, "the first corner should sit on the south edge");
        assertEquals(FAR, c[1].z(), 1e-4F, "the second corner should sit on the south edge");
        assertEquals(NEAR, c[2].z(), 1e-4F, "the third corner should sit on the north edge");
        assertEquals(NEAR, c[3].z(), 1e-4F, "the fourth corner should sit on the north edge");
    }

    private static TankFaces.Quad quad(TankFaces.Side side) {
        return TankFaces.of(side, NEAR, FAR, TOP, 0F, 1F, 0.25F, 0.75F);
    }

    /** Screen-right vector for a viewer standing outside the face and looking at it. */
    private static float[] screenRight(TankFaces.Side side) {
        return switch (side) {
            case NORTH -> new float[] {-1F, 0F, 0F};
            case SOUTH -> new float[] {1F, 0F, 0F};
            case WEST -> new float[] {0F, 0F, 1F};
            case EAST -> new float[] {0F, 0F, -1F};
            case TOP -> new float[] {1F, 0F, 0F};
        };
    }
}
