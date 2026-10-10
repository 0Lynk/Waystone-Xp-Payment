package com.olynk.waystonexppayment.client;

import java.util.Optional;

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
        XpPaymentTankBlockEntity tank = fromWaystone.map(value -> tankBelow(level, value)).orElse(null);
        if (tank == null) {
            // Same fallback the server applies: arriving at a place whose waystone has its own tank can be paid
            // by that tank, so its list entry has to be clickable too.
            tank = tankBelow(level, context.getTargetWaystone());
        }
        if (tank == null) {
            return original;
        }

        // Server-computed pool value from the block entity sync: the client's own rate table cannot know about
        // fluids that were declared by a datapack recipe, which is the server's recipe manager's business.
        int poolXp = tank.poolXpForClient();
        if (poolXp <= 0) {
            return original;
        }
        int costXp = XpRequirementScanner.xpCost(original);
        if (costXp <= 0) {
            return original;
        }
        return new ClientFluidPaymentRequirement(original, XpRequirementScanner.withoutXpCost(original), costXp, poolXp);
    }

    /** The tank directly below a waystone, when it is loaded on this client. */
    private static XpPaymentTankBlockEntity tankBelow(Level level, Waystone waystone) {
        if (waystone == null || !waystone.getDimension().equals(level.dimension())) {
            // Cross-dimension destinations are not loaded on this client; their entry stays gated.
            return null;
        }
        return level.getBlockEntity(waystone.getPos().below()) instanceof XpPaymentTankBlockEntity tank
                ? tank
                : null;
    }
}
