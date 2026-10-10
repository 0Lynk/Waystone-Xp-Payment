package com.olynk.waystonexppayment.client;

import java.util.List;

import com.olynk.waystonexppayment.block.entity.XpPaymentTankBlockEntity;
import com.olynk.waystonexppayment.payment.PaymentSplit;

import net.blay09.mods.waystones.api.requirement.WarpRequirement;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Client-side preview that answers "can this teleport be paid for", mirroring the server's tank-first,
 * player-second split so the button greys out exactly when the trip is unaffordable.
 *
 * <p>The payer is a *loaded* tank, a tank that is only remembered (its chunk is not loaded here), or none:
 * <ul>
 *   <li>loaded with data - mirror the arithmetic against the tank's live contents;</li>
 *   <li>no data yet, or only remembered - stay permissive and let the server decide, because reading "no
 *       information" as "empty" is what once left a full tank's button grey forever.</li>
 * </ul>
 *
 * <p>Never consumes anything: the server performs the real payment through its own requirement.
 */
public final class ClientFluidPaymentRequirement implements WarpRequirement {

    private final WarpRequirement delegate;
    private final WarpRequirement residual;
    private final int costXp;
    private final XpPaymentTankBlockEntity payer;

    /** @param payer the loaded tank that will pay, or {@code null} when a tank exists but cannot be inspected here */
    public ClientFluidPaymentRequirement(WarpRequirement delegate, WarpRequirement residual, int costXp,
                                         XpPaymentTankBlockEntity payer) {
        this.delegate = delegate;
        this.residual = residual;
        this.costXp = costXp;
        this.payer = payer;
    }

    public WarpRequirement delegate() {
        return delegate;
    }

    @Override
    public boolean canAfford(Player player) {
        if (payer != null && payer.hasSyncedData()) {
            // Same rule the server applies: the tank pays first, the player covers the remainder.
            int poolXp = payer.poolXpForClient();
            if (!PaymentSplit.compute(costXp, poolXp, 1, player.totalExperience).affordable()) {
                return false;
            }
        }
        // Everything except the XP cost still has to hold (cooldowns, item costs, ...).
        return residual == null || residual.canAfford(player);
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
