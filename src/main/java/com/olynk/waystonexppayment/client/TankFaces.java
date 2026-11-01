package com.olynk.waystonexppayment.client;

/**
 * The five quads that make up the visible fluid surface, wound so the front face points out of the tank.
 *
 * <p>Winding matters even though the fluid is drawn without culling: the vertices also decide which way the sprite
 * faces, and a quad wound the wrong way shows its texture mirrored to whoever stands outside the tank. The corners
 * are returned in the order the vertex consumer expects - first two along the bottom of the face, last two along its
 * top - with {@code u} running towards screen right as seen from outside.
 */
public final class TankFaces {

    public enum Side {
        NORTH, SOUTH, WEST, EAST, TOP
    }

    /**
     * @param u0        sprite u at screen left
     * @param u1        sprite u at screen right
     * @param vFirst    sprite v for the first two corners (the bottom edge, or the south edge of the top face)
     * @param vSecond   sprite v for the last two corners (the top edge, or the north edge of the top face)
     */
    public record Corner(float x, float y, float z, float u, float v) {
    }

    public record Quad(float normalX, float normalY, float normalZ, Corner[] corners) {
    }

    private TankFaces() {
    }

    public static Quad of(Side side, float near, float far, float top, float u0, float u1, float vFirst,
            float vSecond) {
        return switch (side) {
            case NORTH -> new Quad(0F, 0F, -1F, new Corner[] {
                    new Corner(far, near, near, u0, vFirst), new Corner(near, near, near, u1, vFirst),
                    new Corner(near, top, near, u1, vSecond), new Corner(far, top, near, u0, vSecond)});
            case SOUTH -> new Quad(0F, 0F, 1F, new Corner[] {
                    new Corner(near, near, far, u0, vFirst), new Corner(far, near, far, u1, vFirst),
                    new Corner(far, top, far, u1, vSecond), new Corner(near, top, far, u0, vSecond)});
            case WEST -> new Quad(-1F, 0F, 0F, new Corner[] {
                    new Corner(near, near, near, u0, vFirst), new Corner(near, near, far, u1, vFirst),
                    new Corner(near, top, far, u1, vSecond), new Corner(near, top, near, u0, vSecond)});
            case EAST -> new Quad(1F, 0F, 0F, new Corner[] {
                    new Corner(far, near, far, u0, vFirst), new Corner(far, near, near, u1, vFirst),
                    new Corner(far, top, near, u1, vSecond), new Corner(far, top, far, u0, vSecond)});
            case TOP -> new Quad(0F, 1F, 0F, new Corner[] {
                    new Corner(near, top, far, u0, vFirst), new Corner(far, top, far, u1, vFirst),
                    new Corner(far, top, near, u1, vSecond), new Corner(near, top, near, u0, vSecond)});
        };
    }
}
