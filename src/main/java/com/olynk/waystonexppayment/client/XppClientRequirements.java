package com.olynk.waystonexppayment.client;

import java.util.Optional;

import javax.annotation.Nullable;

import com.olynk.waystonexppayment.WaystoneXpPaymentMod;
import com.olynk.waystonexppayment.block.entity.XpPaymentTankBlockEntity;
import com.olynk.waystonexppayment.payment.TankPlacement;
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
        // Mirror the server's choice of payer: a usable tank under the source wins, otherwise the destination's.
        // "Usable" means not empty, because an empty tank is no payer on the server either.
        TankRef sourceTank = tankBelow(level, fromWaystone.orElse(null));
        TankRef payer = payerAt(sourceTank);
        TankRef targetTank = payer == null ? tankBelow(level, context.getTargetWaystone()) : null;
        if (payer == null) {
            payer = payerAt(targetTank);
        }
        if (payer == null) {
            // "Grey" has three different causes and they lead to different conclusions: no tank was ever seen
            // here, a tank was seen but its chunk is elsewhere (contents unknown), or a loaded tank is empty.
            WaystoneXpPaymentMod.LOGGER.info("[client] no payment tank for this entry (from={}, target={}): "
                            + "under source={}, under target={}", describe(fromWaystone.orElse(null)),
                    describe(context.getTargetWaystone()), describeTank(sourceTank), describeTank(targetTank));
            return original;
        }

        int costXp = XpRequirementScanner.xpCost(original);
        if (costXp <= 0) {
            // Happens with XP *level* requirements, which this addon does not cover.
            WaystoneXpPaymentMod.LOGGER.info("[client] payment tank below the waystone, but this trip has no XP "
                    + "point cost; leaving the vanilla requirement in place");
            return original;
        }
        // Logged because "the button is grey" can mean either "our hook never ran" or "no tank is known here".
        WaystoneXpPaymentMod.LOGGER.info("[client] payment tank for this entry (from={}, target={}): {} "
                        + "(cost {} XP)", describe(fromWaystone.orElse(null)),
                describe(context.getTargetWaystone()),
                payer.loaded() == null ? "chunk not loaded, leaving the decision to the server"
                        : "pool " + payer.loaded().poolXpForClient() + " XP",
                costXp);
        return new ClientFluidPaymentRequirement(original, XpRequirementScanner.withoutXpCost(original), costXp,
                payer.loaded());
    }

    /** A tank below a waystone: the loaded block entity, or {@code null} when only its remembered position exists. */
    private record TankRef(@Nullable XpPaymentTankBlockEntity loaded) {
    }

    /**
     * The tank below a waystone: the loaded block entity when its chunk is here, otherwise the position this
     * client remembers having seen a tank at, or {@code null} when there is none.
     *
     * <p>The remembered case must stay a distinct answer rather than "nothing": for a destination whose chunk is
     * not loaded there is no block entity to inspect, and the server may well find a tank there when it loads the
     * chunk to teleport.
     */
    private static TankRef tankBelow(Level level, Waystone waystone) {
        if (waystone == null || !waystone.getDimension().equals(level.dimension())) {
            // Cross-dimension destinations are not loaded on this client; their entry stays gated.
            return null;
        }
        BlockPos tankPos = TankPlacement.tankPosFor(level, waystone);
        if (level.getBlockEntity(tankPos) instanceof XpPaymentTankBlockEntity tank) {
            XppClientTankRegistry.remember(level, tankPos);
            return new TankRef(tank);
        }
        return XppClientTankRegistry.knows(waystone.getDimension(), tankPos) ? new TankRef(null) : null;
    }

    /** A tank can only pay when it holds something; a remembered tank has unknown contents and must be kept. */
    @Nullable
    private static TankRef payerAt(@Nullable TankRef ref) {
        if (ref == null) {
            return null;
        }
        if (ref.loaded() == null) {
            return ref;
        }
        return ref.loaded().getTank().isEmpty() ? null : ref;
    }

    /** Waystone dimension and position for diagnostics, or {@code none}. */
    private static String describe(Waystone waystone) {
        return waystone == null
                ? "none"
                : waystone.getDimension().location() + "@" + waystone.getPos().toShortString();
    }

    /** What this client knows about one candidate tank, for the log: nothing, position only, or a real tank. */
    private static String describeTank(@Nullable TankRef ref) {
        if (ref == null) {
            return "no tank";
        }
        return ref.loaded() == null
                ? "tank not loaded here"
                : (ref.loaded().getTank().isEmpty() ? "empty tank" : "usable tank")
                        + " at " + ref.loaded().getBlockPos().toShortString();
    }
}
