package com.olynk.waystonexppayment.payment;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.olynk.waystonexppayment.block.entity.XpPaymentTankBlockEntity;
import com.olynk.waystonexppayment.fluid.FluidRates;

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
 * <p>{@link #canAfford(Player)} is intentionally side-effect free. That is what makes the "pool + player both
 * insufficient" case a clean rejection (Waystones reports NotEnoughXp and cancels the teleport) with nothing
 * to roll back.
 */
public final class FluidPaymentRequirement implements WarpRequirement {

    private final WarpRequirement residual;
    private final ServerLevel level;
    private final BlockPos tankPos;
    private final int costXp;

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
        int rate = tank == null ? FluidRates.table().fallbackMbPerXp() : tank.mbPerXp();
        return PaymentSplit.compute(costXp, poolXp, Math.max(1, rate), player.totalExperience);
    }

    @Override
    public boolean canAfford(Player player) {
        if (residual != null && !residual.canAfford(player)) {
            return false;
        }
        return split(player).affordable();
    }

    @Override
    public void consume(Player player) {
        PaymentSplit split = split(player);
        if (!split.affordable()) {
            // Never take a partial payment: Waystones only calls consume() after canAfford() agreed.
            return;
        }

        XpPaymentTankBlockEntity tank = tank();
        if (tank != null && split.fluidUsedMb() > 0) {
            paidFluid = tank.drainInternal(split.fluidUsedMb());
        }
        if (split.xpFromPlayer() > 0) {
            player.giveExperiencePoints(-split.xpFromPlayer());
            paidPlayerXp = split.xpFromPlayer();
        }
        consumed = true;

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
