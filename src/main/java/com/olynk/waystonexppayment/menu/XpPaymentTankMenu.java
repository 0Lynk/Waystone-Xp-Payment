package com.olynk.waystonexppayment.menu;

import javax.annotation.Nullable;

import com.olynk.waystonexppayment.block.entity.XpPaymentTankBlockEntity;
import com.olynk.waystonexppayment.registry.XppRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Menu for the tank screen. It carries no item slots - the screen edits the access list - so the only state
 * it holds is the tank position, which arrives with {@link com.olynk.waystonexppayment.net.XppStatePayload}
 * rather than through the open-screen packet.
 */
public class XpPaymentTankMenu extends AbstractContainerMenu {

    @Nullable
    private BlockPos pos;

    public XpPaymentTankMenu(int windowId, Inventory inventory) {
        super(XppRegistry.XP_PAYMENT_TANK_MENU.get(), windowId);
    }

    public void bind(BlockPos pos) {
        this.pos = pos;
    }

    @Nullable
    public BlockPos pos() {
        return pos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (pos == null) {
            // The state payload has not arrived yet; the menu is still being opened.
            return true;
        }
        return player.level().getBlockEntity(pos) instanceof XpPaymentTankBlockEntity;
    }
}
