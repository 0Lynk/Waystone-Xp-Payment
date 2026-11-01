package com.olynk.waystonexppayment.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.olynk.waystonexppayment.block.entity.XpPaymentTankBlockEntity;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws the fluid the tank holds, so its level is visible through the glass.
 *
 * <p>Ender IO draws five faces (top plus the four sides) from the fluid's still sprite, and Create does the same with
 * a smoothed level; a single-block tank needs no more than that, because Create's extra machinery exists to stitch
 * several tank blocks into one multi-block.
 */
public final class XpPaymentTankRenderer implements BlockEntityRenderer<XpPaymentTankBlockEntity> {

    /** Inner face of the glass panes in model units; 1 unit is 1/16 block. */
    private static final float PANE_NEAR = 1.02F;
    private static final float PANE_FAR = 14.98F;
    private static final float UNIT = 1F / 16F;

    /** Keeps the fluid clear of the glass so the two surfaces do not fight over the same depth. */
    private static final float LEVEL_INSET = 0.25F;

    /** Below this the surface is thinner than a pixel and reads as a stray line rather than as liquid. */
    private static final float LOWEST_VISIBLE = 0.004F;

    private final Map<BlockEntity, FillAnimation> animations = new WeakHashMap<>();

    @Override
    public void render(XpPaymentTankBlockEntity tank, float partialTick, PoseStack pose, MultiBufferSource buffers,
            int packedLight, int packedOverlay) {
        FluidStack fluid = tank.getTank().getFluid();
        int capacity = tank.getTank().getCapacity();
        if (fluid.isEmpty() || capacity <= 0) {
            return;
        }
        float level = animationFor(tank).advance(fluid.getAmount() / (float) capacity, Util.getMillis());
        if (level < LOWEST_VISIBLE) {
            return;
        }
        IClientFluidTypeExtensions properties = IClientFluidTypeExtensions.of(fluid.getFluidType());
        ResourceLocation still = properties.getStillTexture(fluid);
        if (still == null) {
            return;
        }
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(still);
        // RenderType.translucent() does not cull, which is why water is visible from below and why a winding mistake
        // here cannot make the fluid vanish; Create makes the same choice for its tanks.
        draw(pose, buffers.getBuffer(RenderType.translucent()), sprite, properties.getTintColor(fluid), level,
                packedLight);
    }

    /**
     * One renderer serves every tank of this type, so the easing is kept per tank instead of on the renderer.
     *
     * <p>Weak keys let a tank that unloads take its animation with it.
     */
    private FillAnimation animationFor(BlockEntity tank) {
        return animations.computeIfAbsent(tank, ignored -> new FillAnimation());
    }

    private static void draw(PoseStack pose, VertexConsumer buffer, TextureAtlasSprite sprite, int tint, float level,
            int light) {
        float red = (tint >> 16 & 0xFF) / 255F;
        float green = (tint >> 8 & 0xFF) / 255F;
        float blue = (tint & 0xFF) / 255F;
        float near = (PANE_NEAR + LEVEL_INSET) * UNIT;
        float far = (PANE_FAR - LEVEL_INSET) * UNIT;
        float top = near + level * (far - near);
        PoseStack.Pose entry = pose.last();

        // The sides keep the sprite at its own scale, anchored at the bottom: a half-full tank shows the lower half of
        // the texture instead of squashing all of it into the visible band (which is what makes Ender IO's cheap
        // fluids look like they are boiling).
        float u0 = sprite.getU(0F);
        float u1 = sprite.getU(1F);
        float vSide0 = sprite.getV(1F - level);
        float vSide1 = sprite.getV(1F);

        quad(buffer, entry, light, red, green, blue,
                near, near, near,  far, near, near,  far, top, near,  near, top, near,
                u0, vSide1, u1, vSide0, 0F, 0F, -1F);
        quad(buffer, entry, light, red, green, blue,
                far, near, far,  near, near, far,  near, top, far,  far, top, far,
                u0, vSide1, u1, vSide0, 0F, 0F, 1F);
        quad(buffer, entry, light, red, green, blue,
                near, near, far,  near, near, near,  near, top, near,  near, top, far,
                u0, vSide1, u1, vSide0, -1F, 0F, 0F);
        quad(buffer, entry, light, red, green, blue,
                far, near, near,  far, near, far,  far, top, far,  far, top, near,
                u0, vSide1, u1, vSide0, 1F, 0F, 0F);

        float vTop0 = sprite.getV(0F);
        float vTop1 = sprite.getV(1F);
        quad(buffer, entry, light, red, green, blue,
                near, top, near,  near, top, far,  far, top, far,  far, top, near,
                u0, vTop0, u1, vTop1, 0F, 1F, 0F);
    }

    private static void quad(VertexConsumer buffer, PoseStack.Pose pose, int light, float red, float green, float blue,
            float x0, float y0, float z0, float x1, float y1, float z1,
            float x2, float y2, float z2, float x3, float y3, float z3,
            float u0, float v0, float u1, float v1, float normalX, float normalY, float normalZ) {
        vertex(buffer, pose, light, red, green, blue, x0, y0, z0, u0, v0, normalX, normalY, normalZ);
        vertex(buffer, pose, light, red, green, blue, x1, y1, z1, u1, v0, normalX, normalY, normalZ);
        vertex(buffer, pose, light, red, green, blue, x2, y2, z2, u1, v1, normalX, normalY, normalZ);
        vertex(buffer, pose, light, red, green, blue, x3, y3, z3, u0, v1, normalX, normalY, normalZ);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, int light, float red, float green,
            float blue, float x, float y, float z, float u, float v, float normalX, float normalY, float normalZ) {
        buffer.addVertex(pose, x, y, z)
                .setColor(red, green, blue, 1F)
                .setUv(u, v)
                .setLight(light)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setNormal(pose, normalX, normalY, normalZ);
    }
}
