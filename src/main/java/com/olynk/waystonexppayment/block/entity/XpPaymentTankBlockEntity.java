package com.olynk.waystonexppayment.block.entity;

import javax.annotation.Nullable;

import com.olynk.waystonexppayment.auth.AccessList;
import com.olynk.waystonexppayment.config.XppSettings;
import com.olynk.waystonexppayment.fluid.FluidRates;
import com.olynk.waystonexppayment.registry.XppRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * The payment tank: one shared fluid pool plus the access list that decides who may draw from it.
 *
 * <p>Capacity is read once at construction from {@link XppSettings}; a config change applies to newly placed
 * tanks (documented limitation: existing tanks keep the capacity they were created with).
 */
public class XpPaymentTankBlockEntity extends BlockEntity {

    /** Network-only key holding the server-computed XP value of the pool (never written to world data). */
    private static final String SYNC_POOL_XP = "XppPoolXp";

    private final FluidTank tank;
    private AccessList access = new AccessList();
    private int syncedPoolXp = -1;

    public XpPaymentTankBlockEntity(BlockPos pos, BlockState state) {
        super(XppRegistry.XP_PAYMENT_TANK_BE.get(), pos, state);
        this.tank = new FluidTank(XppSettings.current().capacityMb()) {
            @Override
            public boolean isFluidValid(FluidStack stack) {
                // Only declared experience fluids may enter, and only one kind at a time (FluidTank already
                // enforces "same fluid" once non-empty).
                return FluidRates.accepts(stack);
            }

            @Override
            protected void onContentsChanged() {
                setChanged();
            }
        };
    }

    public IFluidHandler getFluidHandler(@Nullable net.minecraft.core.Direction side) {
        return tank;
    }

    public FluidTank getTank() {
        return tank;
    }

    public AccessList access() {
        return access;
    }

    /** XP value of the whole tank, at the rates currently configured for its fluid. */
    public int poolXp() {
        return FluidRates.xpValue(tank.getFluid());
    }

    /**
     * Pool value as the server last computed it, for the client-side teleport preview.
     *
     * <p>The client has its own rate table but not the server's recipe manager, so a fluid declared only by a
     * datapack recipe would look worthless there; the sync tag carries the authoritative number.
     */
    public int poolXpForClient() {
        return syncedPoolXp >= 0 ? syncedPoolXp : poolXp();
    }

    /** mB per XP for the fluid currently in the tank (falls back to the configured default). */
    public int mbPerXp() {
        return FluidRates.mbPerXp(tank.getFluid());
    }

    public FluidStack drainInternal(int amountMb) {
        return tank.drain(amountMb, IFluidHandler.FluidAction.EXECUTE);
    }

    public int fillInternal(FluidStack stack) {
        return tank.fill(stack, IFluidHandler.FluidAction.EXECUTE);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // FluidTank's helpers put the fluid under the conventional "Fluid" key of the tag they are handed, so we
        // hand them our own tag instead of nesting another wrapper. That keeps the block entity readable and
        // writable with vanilla /data commands (verified on a live server).
        tank.writeToNBT(registries, tag);
        tag.put("access", access.save());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(registries, tag);
        if (tag.contains("access")) {
            access = AccessList.load(tag.getCompound("access"));
        }
        // Present only in the network sync tag (getUpdateTag), never in saved world data.
        syncedPoolXp = tag.contains(SYNC_POOL_XP) ? Math.max(0, tag.getInt(SYNC_POOL_XP)) : -1;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        broadcastToClients();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        tag.putInt(SYNC_POOL_XP, poolXp());
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this, BlockEntity::getUpdateTag);
    }

    /** The client-side preview reads the pool value from the block entity, so changes have to reach clients. */
    private void broadcastToClients() {
        if (level instanceof ServerLevel serverLevel && !serverLevel.isClientSide()) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }
}
