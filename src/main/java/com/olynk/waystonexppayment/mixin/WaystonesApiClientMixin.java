package com.olynk.waystonexppayment.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.olynk.waystonexppayment.client.XppClientRequirements;

import net.blay09.mods.waystones.api.WaystoneTeleportContext;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.blay09.mods.waystones.api.requirement.WarpRequirement;

/**
 * Client-only hook around Waystones' requirement resolution.
 *
 * <p>Every client-side preview (selection list, inventory button, hotkey handler) builds its requirement tree
 * through this one static method and then asks it whether the player can afford the teleport, so decorating the
 * result here is what keeps those buttons clickable when the tank below the source is the actual payer.
 *
 * <p>Registered in the {@code client} section of {@code waystone_xp_payment.mixins.json}, so a dedicated
 * server never applies it; {@link XppClientRequirements#decorate} additionally ignores non-client contexts for
 * the single-player case, where one JVM runs both sides.
 */
@Mixin(WaystonesAPI.class)
public class WaystonesApiClientMixin {

    @Inject(method = "resolveRequirements", at = @At("RETURN"), cancellable = true)
    private static void xpp$paymentFromTank(WaystoneTeleportContext context,
                                            CallbackInfoReturnable<WarpRequirement> callback) {
        callback.setReturnValue(XppClientRequirements.decorate(context, callback.getReturnValue()));
    }
}
