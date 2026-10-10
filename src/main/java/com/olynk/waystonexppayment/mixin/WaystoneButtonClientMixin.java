package com.olynk.waystonexppayment.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.olynk.waystonexppayment.client.ClientFluidPaymentRequirement;

import net.blay09.mods.waystones.api.requirement.WarpRequirement;
import net.blay09.mods.waystones.client.gui.widget.WaystoneButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

/**
 * Keeps an open waystone list honest while the tank behind it changes.
 *
 * <p>Waystones decides a button's {@code active} state once, when the entry is built, so draining the tank while
 * the screen is open would leave a button that looks clickable and then fails on click. Re-checking here costs a
 * field read per rendered button, because the tank's XP value is cached in the block entity and refreshed only
 * when its contents change.
 *
 * <p>Only ever disables: Waystones also disables entries on purpose (the waystone you are standing at), and
 * re-enabling those from here would be wrong.
 */
@Mixin(WaystoneButton.class)
public class WaystoneButtonClientMixin {

    @Shadow
    @Final
    private WarpRequirement warpRequirement;

    @Shadow
    protected boolean active;

    @Inject(method = "renderWidget", at = @At("HEAD"))
    private void xpp$greyOutWhenTheTankCannotPay(GuiGraphics guiGraphics, int mouseX, int mouseY,
                                                 float partialTicks, CallbackInfo callback) {
        if (!(warpRequirement instanceof ClientFluidPaymentRequirement requirement)) {
            return;
        }
        Player player = Minecraft.getInstance().player;
        if (player != null && !requirement.canAfford(player)) {
            this.active = false;
        }
    }
}
