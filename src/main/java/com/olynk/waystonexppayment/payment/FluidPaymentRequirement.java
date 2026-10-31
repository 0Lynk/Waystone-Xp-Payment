package com.olynk.waystonexppayment.payment;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.olynk.waystonexppayment.WaystoneXpPaymentMod;
import com.olynk.waystonexppayment.block.entity.XpPaymentTankBlockEntity;
import com.olynk.waystonexppayment.fluid.FluidRates;
import com.olynk.waystonexppayment.fluid.XpRate;

import net.blay09.mods.waystones.api.requirement.WarpRequirement;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Waystones requirement that pays the configured XP cost out of a tank instead of (or before) the player.
 *
 * <p>The tank is resolved lazily from its level/position on every call: a teleport may be validated on one
 * tick and consumed on another, so holding a BlockEntity reference would risk touching stale state.
 *
 * <p>{@link #canAfford(Player)} records the split it approved, and {@link #consume(Player)} settles that plan
 * rather than re-deriving one. Waystones offers no way to abort a teleport once {@code consume} starts - throwing
 * crashes the server, {@code rollback} is never called, and the only cancellation hook runs before the
 * affordability check - so a second, stricter computation here could only choose between "teleport for free" and
 * "crash". Recording the agreement is what makes the charge match the promise, and
 * {@link PaymentSettlement} is what keeps the bill honest when the tank empties in between.
 */
public final class FluidPaymentRequirement implements WarpRequirement {

    private final WarpRequirement residual;
    private final ServerLevel level;
    private final BlockPos tankPos;
    private final int costXp;

    @Nullable
    private PaymentSplit agreed;
    private FluidStack paidFluid = FluidStack.EMPTY;
    private int paidPlayerXp;
    private boolean consumed;

    public FluidPaymentRequirement(@Nullable WarpRequirement residual, ServerLevel level, BlockPos tankPos, int costXp) {
        this.residual = residual;
        this.level = level;
        this.tankPos = tankPos;
        this.costXp = Math.max(0, costXp);
    }

    public int costXp() {
        return costXp;
    }

    public BlockPos tankPos() {
        return tankPos;
    }

    @Nullable
    private XpPaymentTankBlockEntity tank() {
        BlockEntity blockEntity = level.getBlockEntity(tankPos);
        return blockEntity instanceof XpPaymentTankBlockEntity tank ? tank : null;
    }

    private PaymentSplit split(Player player) {
        XpPaymentTankBlockEntity tank = tank();
        int poolXp = tank == null ? 0 : tank.poolXp();
        XpRate rate = tank == null ? null : tank.rate();
        // No rate means no charge: a tank of an unregistered fluid is worth nothing, never a default rate. The
        // placeholder rate is inert - with poolXp = 0 no mB is ever converted with it.
        return PaymentSplit.compute(costXp, poolXp, rate == null ? XpRate.ofBucket(1000) : rate,
                player.totalExperience);
    }

    @Override
    public boolean canAfford(Player player) {
        if (residual != null && !residual.canAfford(player)) {
            agreed = null;
            return false;
        }
        PaymentSplit split = split(player);
        agreed = split.affordable() ? split : null;
        return split.affordable();
    }

    @Override
    public void consume(Player player) {
        // Never null outside a broken call order, and splitting again then is still better than charging nothing.
        PaymentSplit plan = agreed != null ? agreed : split(player);
        agreed = null;

        XpPaymentTankBlockEntity tank = tank();
        if (tank != null && plan.fluidUsedMb() > 0) {
            paidFluid = tank.drainInternal(plan.fluidUsedMb());
        }
        PaymentSettlement settlement = PaymentSettlement.settle(plan, paidFluid.getAmount(), player.totalExperience);
        if (settlement.xpFromPlayer() > 0) {
            player.giveExperiencePoints(-settlement.xpFromPlayer());
            paidPlayerXp = settlement.xpFromPlayer();
        }
        consumed = true;

        if (!settlement.isComplete()) {
            // Nothing else can stop the teleport at this point, so the alternative to a loud log is a silent free
            // ride: the player is already at the destination by now.
            WaystoneXpPaymentMod.LOGGER.error("Teleport from the tank at {} was paid short: tank gave {} of {} mB, "
                            + "player {} paid {} XP, {} XP went unpaid", tankPos.toShortString(),
                    paidFluid.getAmount(), plan.fluidUsedMb(), player.getGameProfile().getName(),
                    settlement.xpFromPlayer(), settlement.xpStillOwed());
        } else if (settlement.xpFromTank() < plan.xpFromFluid()) {
            // Bill still settled, but not the way the plan promised: the tank emptied between the affordability
            // check and here, and the player's own XP covered the gap. Worth a line - it is the same window, one
            // size smaller than the case above.
            WaystoneXpPaymentMod.LOGGER.warn("Tank at {} gave {} of {} mB after agreeing to pay {} XP; player {} "
                            + "covered {} XP instead", tankPos.toShortString(), paidFluid.getAmount(),
                    plan.fluidUsedMb(), plan.xpFromFluid(), player.getGameProfile().getName(),
                    settlement.xpFromPlayer());
        }

        if (residual != null) {
            residual.consume(player);
        }
    }

    @Override
    public void rollback(Player player) {
        if (residual != null) {
            residual.rollback(player);
        }
        if (!consumed) {
            return;
        }

        if (!paidFluid.isEmpty()) {
            XpPaymentTankBlockEntity tank = tank();
            if (tank != null) {
                tank.fillInternal(paidFluid.copy());
            }
            paidFluid = FluidStack.EMPTY;
        }
        if (paidPlayerXp > 0) {
            player.giveExperiencePoints(paidPlayerXp);
            paidPlayerXp = 0;
        }
        consumed = false;
    }

    @Override
    public void appendHoverText(Player player, List<Component> tooltip) {
        if (costXp > 0) {
            tooltip.add(Component.translatable("gui.waystone_xp_payment.fluid_cost", costXp)
                    .withStyle(ChatFormatting.AQUA));
            PaymentSplit plan = split(player);
            // Same sentence the client preview prints, so the two paths cannot read as two different features.
            tooltip.add(Component.translatable("gui.waystone_xp_payment.tank_cover", plan.xpFromFluid(), costXp)
                    .withStyle(plan.affordable() ? ChatFormatting.GRAY : ChatFormatting.RED));
        }
        if (residual != null) {
            residual.appendHoverText(player, tooltip);
        }
    }

    @Override
    public boolean isEmpty() {
        return costXp <= 0 && (residual == null || residual.isEmpty());
    }
}
