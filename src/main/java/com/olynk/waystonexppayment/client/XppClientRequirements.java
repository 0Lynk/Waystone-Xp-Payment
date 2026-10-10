package com.olynk.waystonexppayment.client;

import java.util.Optional;

import com.olynk.waystonexppayment.WaystoneXpPaymentMod;
import com.olynk.waystonexppayment.block.entity.XpPaymentTankBlockEntity;
import com.olynk.waystonexppayment.payment.XpRequirementScanner;

import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneTeleportContext;
import net.blay09.mods.waystones.api.requirement.WarpRequirement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Turns the client's config-resolved requirement tree into one that also accepts the tank below the teleport
 * source as a payer.
 *
 * <p>Called from a client mixin around {@code WaystonesAPI.resolveRequirements}, which is the single place
 * every client preview builds its requirement tree from (selection list, inventory button, hotkey handler),
 * so the button state and the hover/icon rendering all agree.
 */
public final class XppClientRequirements {

    private XppClientRequirements() {
    }

    /** @return the original requirement, or a wrapping one when a tank below the source could pay. */
    public static WarpRequirement decorate(WaystoneTeleportContext context, WarpRequirement original) {
        if (context == null || original == null) {
            return original;
        }
        if (!(context.getEntity() instanceof Player player)) {
            return original;
        }
        Level level = player.level();
        if (level == null || !level.isClientSide()) {
            // Single-player runs both sides in one JVM: never touch the authoritative server-side resolution.
            return original;
        }

        Optional<Waystone> fromWaystone = context.getFromWaystone();
        boolean tank = fromWaystone.map(value -> tankBelow(level, value)).orElse(false);
        if (!tank) {
            // Same fallback the server applies: arriving at a place whose waystone has its own tank can be paid
            // by that tank, so its list entry has to be clickable too.
            tank = tankBelow(level, context.getTargetWaystone());
        }
        if (!tank) {
            // Logged as well as the positive case: "the button is grey" is answered differently depending on
            // whether this hook ran and found nothing, or never ran at all.
            WaystoneXpPaymentMod.LOGGER.info("[client] no payment tank for this entry (from={}, target={})",
                    describe(fromWaystone.orElse(null)), describe(context.getTargetWaystone()));
            return original;
        }

        // Presence of a tank is all the client needs to know: whether that tank can actually cover this trip is
        // the server's decision, and answering it from a synced snapshot is what used to leave the button grey
        // after the tank's contents changed.
        int costXp = XpRequirementScanner.xpCost(original);
        if (costXp <= 0) {
            // Happens with XP *level* requirements, which this addon does not cover.
            WaystoneXpPaymentMod.LOGGER.info("[client] payment tank below the waystone, but this trip has no XP "
                    + "point cost; leaving the vanilla requirement in place");
            return original;
        }
        // Logged because "the button is grey" can mean either "our hook never ran" or "no tank is known here".
        WaystoneXpPaymentMod.LOGGER.info("[client] payment tank known for this entry (from={}, target={}): "
                        + "allowing the teleport button (cost {} XP)", describe(fromWaystone.orElse(null)),
                describe(context.getTargetWaystone()), costXp);
        return new ClientFluidPaymentRequirement(original, XpRequirementScanner.withoutXpCost(original), costXp);
    }

    /**
     * Whether a payment tank sits directly below a waystone.
     *
     * <p>Checked against the loaded block entity first, then against tanks this client has seen before: for a
     * destination whose chunk is not loaded there is no block entity to inspect, and without the remembered
     * position the "travel back to the tank" entry would stay disabled forever.
     */
    private static boolean tankBelow(Level level, Waystone waystone) {
        if (waystone == null || !waystone.getDimension().equals(level.dimension())) {
            // Cross-dimension destinations are not loaded on this client; their entry stays gated.
            return false;
        }
        BlockPos tankPos = waystone.getPos().below();
        if (level.getBlockEntity(tankPos) instanceof XpPaymentTankBlockEntity) {
            XppClientTankRegistry.remember(level, tankPos);
            return true;
        }
        return XppClientTankRegistry.knows(waystone.getDimension(), tankPos);
    }

    /** Waystone dimension and position for diagnostics, or {@code none}. */
    private static String describe(Waystone waystone) {
        return waystone == null
                ? "none"
                : waystone.getDimension().location() + "@" + waystone.getPos().toShortString();
    }
}
