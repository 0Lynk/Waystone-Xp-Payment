package com.olynk.waystonexppayment.event;

import javax.annotation.Nullable;

import com.olynk.waystonexppayment.WaystoneXpPaymentMod;
import com.olynk.waystonexppayment.auth.TeamResolvers;
import com.olynk.waystonexppayment.block.entity.XpPaymentTankBlockEntity;
import com.olynk.waystonexppayment.config.XppSettings;
import com.olynk.waystonexppayment.fluid.FluidRates;
import com.olynk.waystonexppayment.payment.FluidPaymentRequirement;
import com.olynk.waystonexppayment.payment.XpRequirementScanner;

import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneTeleportContext;
import net.blay09.mods.waystones.api.event.WaystoneTeleportEvent;
import net.blay09.mods.waystones.api.requirement.WarpRequirement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Replaces the XP part of a teleport's requirements when either end of the trip has a payment tank directly
 * below it: the source waystone's tank first, then the destination waystone's.
 *
 * <p>Timing: Waystones fires {@link WaystoneTeleportEvent.Pre} from {@code WaystoneTeleportManager} after the
 * requirement tree has been resolved (config modifiers included) and before it validates/consumes it, which
 * is exactly where the real cost can be read and swapped for a fluid payment.
 */
public final class XppTeleportHandler {

    private XppTeleportHandler() {
    }

    public static void onTeleportPre(WaystoneTeleportEvent.Pre event) {
        WaystoneTeleportContext context = event.getContext();
        if (!(context.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (XppSettings.current().skipPaymentInCreative() && player.getAbilities().instabuild) {
            return;
        }

        ServerLevel playerLevel = player.serverLevel();

        // The tank can hang under either end of the trip: below the waystone the player travels from, or below
        // the destination waystone (arriving somewhere should be payable by that place's pool too).
        Payer payer = payerAt(context.getFromWaystone().orElse(null), playerLevel, player, "source");
        if (payer == null) {
            payer = payerAt(context.getTargetWaystone(), playerLevel, player, "destination");
        }
        if (payer == null) {
            return;
        }

        WarpRequirement current = context.getRequirements();
        int cost = XpRequirementScanner.xpCost(current);
        if (cost <= 0) {
            return;
        }
        if (XpRequirementScanner.hasLevelRequirement(current)) {
            WaystoneXpPaymentMod.LOGGER.debug(
                    "XP level requirements are not covered by the fluid tank; the player still pays those");
        }

        WaystoneXpPaymentMod.LOGGER.info("Teleport paid from the {} tank at {}: {} (cost {} XP, pool {} XP)",
                payer.kind(), payer.pos().toShortString(), FluidRates.describe(payer.tank().getTank().getFluid()),
                cost, payer.tank().poolXp());

        WarpRequirement residual = XpRequirementScanner.withoutXpCost(current);
        event.setRequirements(new FluidPaymentRequirement(residual, payer.level(), payer.pos(), cost));
    }

    /** The paying tank below a waystone, or {@code null} when there is none the player may use. */
    @Nullable
    private static Payer payerAt(@Nullable Waystone waystone, ServerLevel playerLevel, ServerPlayer player,
                                 String kind) {
        if (waystone == null) {
            return null;
        }
        ServerLevel level = waystone.getDimension().equals(playerLevel.dimension())
                ? playerLevel
                : player.server.getLevel(waystone.getDimension());
        if (level == null) {
            return null;
        }
        BlockPos tankPos = waystone.getPos().below();
        if (!(level.getBlockEntity(tankPos) instanceof XpPaymentTankBlockEntity tank) || tank.getTank().isEmpty()) {
            // Unloaded destination chunks land here on purpose: fall back to the player rather than guess.
            return null;
        }
        if (!tank.access().allows(player, TeamResolvers.get())) {
            // Not authorised to draw from this pool: the player pays normally instead of being blocked.
            return null;
        }
        return new Payer(kind, level, tankPos, tank);
    }

    private record Payer(String kind, ServerLevel level, BlockPos pos, XpPaymentTankBlockEntity tank) {
    }
}
