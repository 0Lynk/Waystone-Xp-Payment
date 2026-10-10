package com.olynk.waystonexppayment.client;

import net.blay09.mods.waystones.api.requirement.WarpRequirement;
import net.blay09.mods.waystones.client.requirement.RequirementClientRegistry;
import net.blay09.mods.waystones.client.requirement.RequirementRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

/**
 * Renders the wrapped requirement by delegating to the renderer of the requirement it wraps, so the vanilla
 * XP-cost icon (and any cooldown/item icon in a combined tree) keeps working when the tank is paying.
 */
public class ClientFluidPaymentRenderer implements RequirementRenderer<ClientFluidPaymentRequirement> {

    @Override
    public void renderWidget(Player player, ClientFluidPaymentRequirement requirement, GuiGraphics guiGraphics,
                             int mouseX, int mouseY, float partialTicks, int x, int y) {
        RequirementRenderer<WarpRequirement> delegate = RequirementClientRegistry.getRenderer(requirement.delegate());
        if (delegate != null) {
            delegate.renderWidget(player, requirement.delegate(), guiGraphics, mouseX, mouseY, partialTicks, x, y);
        }
    }

    @Override
    public int getWidth(Player player, ClientFluidPaymentRequirement requirement) {
        RequirementRenderer<WarpRequirement> delegate = RequirementClientRegistry.getRenderer(requirement.delegate());
        return delegate == null ? 16 : delegate.getWidth(player, requirement.delegate());
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
