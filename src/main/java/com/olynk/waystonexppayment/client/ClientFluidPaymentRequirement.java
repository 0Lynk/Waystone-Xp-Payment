package com.olynk.waystonexppayment.client;

import java.util.List;

import com.olynk.waystonexppayment.payment.PaymentSplit;

import net.blay09.mods.waystones.api.requirement.WarpRequirement;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Client-side mirror of {@code FluidPaymentRequirement}'s affordability rule.
 *
 * <p>Waystones decides whether the teleport buttons are clickable on the <em>client</em>
 * ({@code WaystoneButton} asks the resolved requirement tree, which only knows the config-driven XP cost).
 * Without this wrapper a player whose own XP is too low sees a greyed-out button even when the tank below the
 * source could pay, and nothing is ever sent to the server.
 *
 * <p>It never consumes anything on its own: {@link #consume(Player)} and {@link #rollback(Player)} are no-ops
 * because the server performs the real split in {@code FluidPaymentRequirement}.
 */
public final class ClientFluidPaymentRequirement implements WarpRequirement {

    private final WarpRequirement delegate;
    private final WarpRequirement residual;
    private final int costXp;
    private final int poolXp;

    public ClientFluidPaymentRequirement(WarpRequirement delegate, WarpRequirement residual, int costXp, int poolXp) {
        this.delegate = delegate;
        this.residual = residual;
        this.costXp = costXp;
        this.poolXp = poolXp;
    }

    public WarpRequirement delegate() {
        return delegate;
    }

    @Override
    public boolean canAfford(Player player) {
        if (residual != null && !residual.canAfford(player)) {
            return false;
        }
        // The tank pays first, the player covers the remainder - the same arithmetic the server will apply.
        return PaymentSplit.compute(costXp, poolXp, 1, player.totalExperience).affordable();
    }

    @Override
    public void consume(Player player) {
        // Client-side preview only; the server consumes through its own requirement.
    }

    @Override
    public void rollback(Player player) {
        // See consume().
    }

    @Override
    public void appendHoverText(Player player, List<Component> tooltip) {
        if (delegate != null) {
            delegate.appendHoverText(player, tooltip);
        }
        if (costXp > 0) {
            tooltip.add(Component.translatable("gui.waystone_xp_payment.fluid_cost", costXp)
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    @Override
    public boolean isEmpty() {
        // Never empty while it is offering to pay: the whole point is that the preview stays enabled.
        return false;
    }
}
