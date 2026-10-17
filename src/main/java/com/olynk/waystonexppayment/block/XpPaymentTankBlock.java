package com.olynk.waystonexppayment.block;

import com.mojang.serialization.MapCodec;
import com.olynk.waystonexppayment.auth.TeamResolvers;
import com.olynk.waystonexppayment.block.entity.XpPaymentTankBlockEntity;
import com.olynk.waystonexppayment.config.XppSettings;
import com.olynk.waystonexppayment.data.TankPoolIndex;
import com.olynk.waystonexppayment.menu.XpPaymentTankMenu;
import com.olynk.waystonexppayment.net.XppStatePayload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidUtil;

/**
 * The payment tank block. Stores the shared experience-fluid pool that pays for Waystones teleports of the
 * teleport source directly above it.
 */
public class XpPaymentTankBlock extends BaseEntityBlock {

    public static final MapCodec<XpPaymentTankBlock> CODEC = simpleCodec(XpPaymentTankBlock::new);

    public XpPaymentTankBlock(Properties properties) {
        super(properties);
    }

    /**
     * Drops the remembered pool of this slot when the block itself goes away.
     *
     * <p>Not in {@code BlockEntity#setRemoved}: that also runs when a chunk unloads, and erasing the entry then
     * would defeat the whole index - the point is to keep knowing about a pool whose chunk is not resident.
     * A slot whose tank is gone but whose index entry lingers would quote a payment the server cannot collect,
     * which is the one direction of error this feature must not produce.
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            TankPoolIndex.get(serverLevel.getServer()).forget(level.dimension().location(), pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new XpPaymentTankBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) {
            return;
        }
        // The placing player becomes the owner; without a player (a dispenser, /setblock) the tank stays
        // unclaimed until somebody edits it through the screen.
        if (placer instanceof ServerPlayer player
                && level.getBlockEntity(pos) instanceof XpPaymentTankBlockEntity tank) {
            tank.setOwner(player.getUUID(), player.getGameProfile().getName());
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!XppSettings.current().allowBucketInteraction()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof XpPaymentTankBlockEntity tank)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        // Lets buckets, and any modded fluid container, fill or drain the tank through the standard capability.
        return FluidUtil.interactWithFluidHandler(player, hand, tank.getFluidHandler(hitResult.getDirection()))
                ? ItemInteractionResult.SUCCESS
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof XpPaymentTankBlockEntity tank) {
            // The open-screen packet carries no extra data: the position travels with the state payload below,
            // which is also what makes the screen work after a server-side /reload without re-opening.
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (windowId, inventory, p) -> new XpPaymentTankMenu(windowId, inventory),
                    Component.translatable("block.waystone_xp_payment.xp_payment_tank")));
            XppStatePayload.sendTo(serverPlayer, pos, tank, TeamResolvers.get().teamsOf(serverPlayer));
        }
        return InteractionResult.SUCCESS;
    }
}
