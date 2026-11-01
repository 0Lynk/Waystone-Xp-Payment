package com.olynk.waystonexppayment.client;

/**
 * Decides how brightly the fluid surface is lit.
 *
 * <p>A fluid that declares a light level is drawn at full brightness, the same way lava is: without this the fluid
 * looks dull inside an unlit room even though the block itself casts light, and the two would disagree about whether
 * the contents glow. Everything else keeps the light of the block it sits in.
 */
public final class TankLight {

    private TankLight() {
    }

    /**
     * @param packedLight     the packed light at the tank's position
     * @param fluidLuminance  what {@code FluidType#getLightLevel} reports for the fluid, 0..15
     * @param fullBrightness  the packed light value meaning "ignore the surroundings"
     */
    public static int packed(int packedLight, int fluidLuminance, int fullBrightness) {
        return fluidLuminance > 0 ? fullBrightness : packedLight;
    }
}
