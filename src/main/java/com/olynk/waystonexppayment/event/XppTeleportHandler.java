package com.olynk.waystonexppayment.event;

import java.util.List;

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
        XpPaymentTankBlockEntity tank = tankBelow(level, waystone.getPos(), budget);
        if (tank == null || tank.getTank().isEmpty()) {
            return null;
        }
        // The slot the tank itself reports, not the one that was computed: when the waystone's shape could not
        // be observed the tank can be in either candidate, and the requirement must name the block entity that
        // is actually going to be drained.
        BlockPos tankPos = tank.getBlockPos();
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
     * The tank under {@code waystonePos}, probing every candidate slot and loading the chunk when the
     * remote-destination setting allows it.
     *
     * <p>Why candidates: whether the tank hangs one or two blocks down depends on whether the recorded waystone
     * position is the upper half, which cannot be observed while the chunk is not here - see
     * {@link TankPlacement}. Both candidates are tried, so a destination in an unloaded chunk pays from the slot
     * the tank is really in instead of reporting an empty pool.
     *
     * <p>Why the load is allowed at all: without it, a destination whose chunk is not resident can never pay,
     * which is the common case - the point of a waystone is to travel somewhere you are not standing. The chunk
     * is not a gamble either: a destination waystone's block lives in it, so the chunk exists on disk, and the
     * teleport loads the very same chunk moments later anyway.
     */
    @Nullable
    private static XpPaymentTankBlockEntity tankBelow(ServerLevel level, BlockPos waystonePos, PayerBudget budget) {
        List<BlockPos> candidates = TankPlacement.candidateTankPositions(level, waystonePos);
        XpPaymentTankBlockEntity resident = residentTankAt(level, candidates);
        if (!shouldLoadForTank(resident != null, level.isLoaded(waystonePos))) {
            return resident;
        }
        // Every candidate shares the waystone's own chunk (they differ in Y only), so one permit covers the whole
        // candidate set and the waystone position is both the chunk to ask for and the slot to bill.
        String slot = TankPoolIndex.key(level.dimension().location(), waystonePos);
        long now = System.currentTimeMillis();
        if (!budget.requestLoad(slot, now)) {
            // Budget spent, or this slot already missed a moment ago: fall back to the player instead of paying
            // for the same fruitless load twice.
            return null;
        }
        WaystoneXpPaymentMod.LOGGER.debug("Loading the chunk at {} to reach the payment tank below a waystone",
                waystonePos.toShortString());
        level.getChunk(waystonePos.getX() >> 4, waystonePos.getZ() >> 4, ChunkStatus.FULL, true);
        XpPaymentTankBlockEntity loaded = residentTankAt(level, candidates);
        if (loaded == null) {
            budget.rememberMiss(slot, now, REMOTE_PAYER_MEMO_MILLIS);
        }
        return loaded;
    }

    /**
     * Whether loading this waystone's chunk can still turn "no tank found" into "tank found".
     *
     * <p>False when the chunk is already resident: both candidate slots were just probed inside it, so a load
     * reveals nothing. Asking the budget anyway spends the attempt's only permit on that no-op - and the source
     * end of a trip is resident by definition and often has no tank, so it used to eat the permit the
     * destination needed. The first click then fell back to the player's own XP and failed; the retry succeeded
     * only because {@link PayerBudget}'s memo absorbed the wasted request for a few seconds.
     */
    static boolean shouldLoadForTank(boolean tankFoundHere, boolean chunkResident) {
        return !tankFoundHere && !chunkResident;
    }

    /**
     * The first candidate whose chunk is already resident and which actually holds a payment tank.
     *
     * <p>{@code isLoaded} before {@code getBlockEntity}: the block entity lookup falls back to
     * {@code getChunkAt}, which loads the chunk with {@code requireChunk=true}. Probing without that guard would
     * pull chunks in behind the budget's back - the very bypass this handler is here to prevent.
     */
    @Nullable
    private static XpPaymentTankBlockEntity residentTankAt(ServerLevel level, List<BlockPos> candidates) {
        for (BlockPos candidate : candidates) {
            if (!level.isLoaded(candidate)) {
                continue;
            }
            if (level.getBlockEntity(candidate) instanceof XpPaymentTankBlockEntity tank) {
                return tank;
            }
        }
        return null;
    }

    private record Payer(String kind, ServerLevel level, BlockPos pos, XpPaymentTankBlockEntity tank) {
    }
}
