package com.olynk.waystonexppayment.event;

import java.util.Optional;

import com.olynk.waystonexppayment.WaystoneXpPaymentMod;
import com.olynk.waystonexppayment.auth.TeamResolvers;
import com.olynk.waystonexppayment.block.entity.XpPaymentTankBlockEntity;
import com.olynk.waystonexppayment.config.XppSettings;
import com.olynk.waystonexppayment.payment.FluidPaymentRequirement;
import com.olynk.waystonexppayment.payment.XpRequirementScanner;

import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneTeleportContext;
import net.blay09.mods.waystones.api.event.WaystoneTeleportEvent;
import net.blay09.mods.waystones.api.requirement.WarpRequirement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Replaces the XP part of a teleport's requirements when the teleport source has a payment tank directly
 * below it.
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

        Optional<Waystone> fromWaystone = context.getFromWaystone();
        if (fromWaystone.isEmpty()) {
            // Warp items / scrolls have no source block, so there is nothing to hang a tank under.
            return;
        }

        Waystone source = fromWaystone.get();
        ServerLevel level = player.serverLevel();
        if (!source.getDimension().equals(level.dimension())) {
            return;
        }

        BlockPos tankPos = source.getPos().below();
        BlockEntity blockEntity = level.getBlockEntity(tankPos);
        if (!(blockEntity instanceof XpPaymentTankBlockEntity tank) || tank.getTank().isEmpty()) {
            return;
        }
        if (!tank.access().allows(player, TeamResolvers.get())) {
            // Not authorised to draw from this pool: the player pays normally instead of being blocked.
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

        WarpRequirement residual = XpRequirementScanner.withoutXpCost(current);
        event.setRequirements(new FluidPaymentRequirement(residual, level, tankPos, cost));
    }
}
