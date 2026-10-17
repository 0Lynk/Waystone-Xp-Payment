package com.olynk.waystonexppayment.event;

import javax.annotation.Nullable;

import com.olynk.waystonexppayment.WaystoneXpPaymentMod;
import com.olynk.waystonexppayment.auth.TeamResolvers;
import com.olynk.waystonexppayment.block.entity.XpPaymentTankBlockEntity;
import com.olynk.waystonexppayment.config.XppSettings;
import com.olynk.waystonexppayment.data.TankPoolIndex;
import com.olynk.waystonexppayment.fluid.FluidRates;
import com.olynk.waystonexppayment.payment.FluidPaymentRequirement;
import com.olynk.waystonexppayment.payment.PayerBudget;
import com.olynk.waystonexppayment.payment.TankPlacement;
import com.olynk.waystonexppayment.payment.XpRequirementScanner;

import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneTeleportContext;
import net.blay09.mods.waystones.api.event.WaystoneTeleportEvent;
import net.blay09.mods.waystones.api.requirement.WarpRequirement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Replaces the XP part of a teleport's requirements when either end of the trip has a payment tank directly
 * below it: the source waystone's tank first, then the destination waystone's.
 *
 * <p>Timing: Waystones fires {@link WaystoneTeleportEvent.Pre} from {@code WaystoneTeleportManager} after the
 * requirement tree has been resolved (config modifiers included) and before it validates/consumes it, which
 * is exactly where the real cost can be read and swapped for a fluid payment.
 */
public final class XppTeleportHandler {

    /** How long a destination slot whose chunk could not be loaded is left alone before it is retried. */
    private static final long REMOTE_PAYER_MEMO_MILLIS = 5_000L;

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

        // One budget per attempt. The source end is resident by definition (the player is standing at it), so
        // in practice this is the destination's single allowed load.
        XppSettings settings = XppSettings.current();
        PayerBudget budget = new PayerBudget(
                settings.remoteDestinationPayments() ? settings.remotePaymentChunkLoads() : 0);

        // The tank can hang under either end of the trip: below the waystone the player travels from, or below
        // the destination waystone (arriving somewhere should be payable by that place's pool too).
        Payer payer = payerAt(context.getFromWaystone().orElse(null), playerLevel, player, "source", budget);
        if (payer == null) {
            payer = payerAt(context.getTargetWaystone(), playerLevel, player, "destination", budget);
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
                                 String kind, PayerBudget budget) {
        if (waystone == null) {
            return null;
        }
        ServerLevel level = waystone.getDimension().equals(playerLevel.dimension())
                ? playerLevel
                : player.server.getLevel(waystone.getDimension());
        if (level == null) {
            return null;
        }
        BlockPos tankPos = TankPlacement.tankPosFor(level, waystone);
        XpPaymentTankBlockEntity tank = tankAt(level, tankPos, budget);
        if (tank == null || tank.getTank().isEmpty()) {
            return null;
        }
        if (!tank.access().allows(player, TeamResolvers.get())) {
            // Not authorised to draw from this pool: the player pays normally instead of being blocked.
            return null;
        }
        if (tank.poolXp() <= 0) {
            // The tank holds something it cannot be paid with. Say so once per attempt: this is the only way to
            // tell "the fluid is not recognized" apart from "the pool is empty" after the fact.
            WaystoneXpPaymentMod.LOGGER.warn("Tank at {} cannot pay: {} (add the fluid to fluidConversions or "
                            + "declare it with a waystone_xp_payment:xp_fluid recipe)",
                    tankPos.toShortString(), FluidRates.describe(tank.getTank().getFluid()));
        }
        return new Payer(kind, level, tankPos, tank);
    }

    /**
     * The tank at {@code tankPos}, loading its chunk when the remote-destination setting allows it.
     *
     * <p>Without the load, a destination whose chunk is not resident can never pay, which is the common case:
     * the point of a waystone is to travel somewhere you are not standing. The chunk is not a gamble either -
     * a destination waystone's block lives in it, so the chunk exists on disk; this reads it in, and the
     * teleport loads the very same chunk moments later anyway.
     */
    @Nullable
    private static XpPaymentTankBlockEntity tankAt(ServerLevel level, BlockPos tankPos, PayerBudget budget) {
        if (level.getBlockEntity(tankPos) instanceof XpPaymentTankBlockEntity loaded) {
            return loaded;
        }
        String slot = TankPoolIndex.key(level.dimension().location(), tankPos);
        if (!budget.requestLoad(slot, System.currentTimeMillis(), REMOTE_PAYER_MEMO_MILLIS)) {
            // Budget spent, or this exact slot was tried a moment ago: fall back to the player instead of
            // paying for the same chunk load twice.
            return null;
        }
        WaystoneXpPaymentMod.LOGGER.debug("Loading the chunk at {} to reach the payment tank below a waystone",
                tankPos.toShortString());
        level.getChunk(tankPos.getX() >> 4, tankPos.getZ() >> 4, ChunkStatus.FULL, true);
        return level.getBlockEntity(tankPos) instanceof XpPaymentTankBlockEntity tank ? tank : null;
    }

    private record Payer(String kind, ServerLevel level, BlockPos pos, XpPaymentTankBlockEntity tank) {
    }
}
