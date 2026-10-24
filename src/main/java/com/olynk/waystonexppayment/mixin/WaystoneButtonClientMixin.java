package com.olynk.waystonexppayment.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.olynk.waystonexppayment.WaystoneXpPaymentMod;
import com.olynk.waystonexppayment.client.ClientFluidPaymentRequirement;
import com.olynk.waystonexppayment.client.PaymentGreyState;

import net.blay09.mods.waystones.api.requirement.WarpRequirement;
import net.blay09.mods.waystones.client.gui.widget.WaystoneButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.world.entity.player.Player;

/**
 * Keeps an open waystone list honest while the tank behind it changes.
 *
 * <p>Waystones decides a button's {@code active} state once, when the entry is built, so draining the tank while
 * the screen is open would leave a button that looks clickable and then fails on click. Re-checking here costs a
 * field read per rendered button, because the tank's XP value is cached in the block entity and refreshed only
 * when its contents change.
 *
 * <p>Grey is taken and given back by the same rule ({@link PaymentGreyState}): the button is re-enabled once the
 * tank pays again, but only when this mixin was the one that turned it off.
 */
@Mixin(WaystoneButton.class)
public class WaystoneButtonClientMixin {

    @Shadow
    @Final
    private WarpRequirement warpRequirement;

    @Unique
    private PaymentGreyState xpp$greyState;

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
        Boolean next = xpp$greyState().nextActive(requirement.canAfford(player), widget.active);
        if (next == null) {
            return;
        }
        widget.active = next;
        // Only on a transition: this runs every frame, and the numbers are what makes a grey button explainable.
        WaystoneXpPaymentMod.LOGGER.info("[client] {} this entry: {}", next ? "re-enabled" : "greyed out",
                requirement.describeAffordability(player));
    }

    @Unique
    private PaymentGreyState xpp$greyState() {
        // Not a field initializer: Mixin merges those into the constructor, which is exactly where a
        // half-built field would already be readable by Waystones' own affordability check.
        if (xpp$greyState == null) {
            xpp$greyState = new PaymentGreyState();
        }
        return xpp$greyState;
    }
}
