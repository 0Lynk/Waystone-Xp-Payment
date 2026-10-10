package com.olynk.waystonexppayment.client;

import java.util.List;

import net.blay09.mods.waystones.api.requirement.WarpRequirement;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Client-side wrapper that lets a teleport be attempted when a payment tank sits below either end of the trip.
 *
 * <p>Waystones decides whether its teleport buttons are clickable on the <em>client</em>, from the
 * config-resolved XP requirement, which knows nothing about tanks. This wrapper answers "somebody is willing to
 * pay for this" and leaves the arithmetic to the server: the server's requirement does the tank-first,
 * player-second split and rejects the teleport when neither side covers it, at which point the player sees
 * Waystones' ordinary NotEnoughXp message.
 *
 * <p>Deliberately does <em>not</em> look at the tank's synced contents. An earlier version did, and a stale or
 * missing block-entity snapshot made a perfectly usable tank look empty forever - the button stayed grey even
 * after the unrecognized fluid had been drained and replaced.
 */
public final class ClientFluidPaymentRequirement implements WarpRequirement {

    private final WarpRequirement delegate;
    private final WarpRequirement residual;
    private final int costXp;

    public ClientFluidPaymentRequirement(WarpRequirement delegate, WarpRequirement residual, int costXp) {
        this.delegate = delegate;
        this.residual = residual;
        this.costXp = costXp;
    }

    public WarpRequirement delegate() {
        return delegate;
    }

    @Override
    public boolean canAfford(Player player) {
        // Everything except the XP cost still has to hold (cooldowns, item costs, ...); the XP part is the
        // server's call, because only the server knows what the tank contains right now.
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
