package com.olynk.waystonexppayment.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.olynk.waystonexppayment.WaystoneXpPaymentMod;
import com.olynk.waystonexppayment.client.ClientFluidPaymentRequirement;

import net.blay09.mods.waystones.api.requirement.WarpRequirement;
import net.blay09.mods.waystones.client.gui.widget.WaystoneButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.world.entity.player.Player;

/**
 * Derives a button's clickable state from the tank, every frame, instead of remembering it.
 *
 * <p>Waystones decides {@code active} once, when the entry is built, so draining the tank while the screen is open
 * would leave a button that looks clickable and then fails on click. Re-deriving costs one split per rendered
 * button, which measures at tens of nanoseconds and reads the tank's cached XP value.
 *
 * <p>Why this is a plain {@code &&} and not a "who greyed it" ledger: for the buttons this mixin touches, Waystones'
 * own verdict and the tank's affordability are the same answer. The other two ways Waystones greys a button - a
 * null player, and the waystone you are standing on - can never reach this code: the first cannot render a widget,
 * and the second keeps the vanilla requirement (see {@code XppClientRequirements.decorate}), so the
 * {@code instanceof} below never matches it. A ledger of past decisions would therefore only be re-deriving a
 * constant, and the one this mod kept is what turned a single unaffordable frame into a permanently dead button.
 */
@Mixin(WaystoneButton.class)
public class WaystoneButtonClientMixin {

    @Shadow
    @Final
    private WarpRequirement warpRequirement;

    @Inject(method = "renderWidget", at = @At("HEAD"))
    private void xpp$applyPaymentState(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks,
                                       CallbackInfo callback) {
        if (!(warpRequirement instanceof ClientFluidPaymentRequirement requirement)) {
            return;
        }
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        // `active` is declared in AbstractWidget, not in WaystoneButton, and @Shadow only resolves members a
        // class declares itself: shadowing it compiled fine and then failed Mixin APPLY the first time this
        // button was constructed, which took the whole screen - and the client - down. The field is public,
        // so reaching it through the supertype is all that is needed.
        AbstractWidget widget = (AbstractWidget) (Object) this;
        boolean affordable = requirement.canAfford(player);
        if (widget.active == affordable) {
            return;
        }
        widget.active = affordable;
        // Only on a transition: this runs every frame, and the numbers are what makes a grey button explainable.
        WaystoneXpPaymentMod.LOGGER.info("[client] {} this entry: {}", affordable ? "re-enabled" : "greyed out",
                requirement.describeAffordability(player));
    }
}
