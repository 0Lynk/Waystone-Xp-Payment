package com.olynk.waystonexppayment.block.entity;

import javax.annotation.Nullable;

import com.olynk.waystonexppayment.auth.AccessList;
import com.olynk.waystonexppayment.config.XppSettings;
import com.olynk.waystonexppayment.fluid.FluidRates;
import com.olynk.waystonexppayment.registry.XppRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
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

    private final FluidTank tank;
    private AccessList access = new AccessList();

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
        tag.put("tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.put("access", access.save());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("tank")) {
            tank.readFromNBT(registries, tag.getCompound("tank"));
        }
        if (tag.contains("access")) {
            access = AccessList.load(tag.getCompound("access"));
        }
    }
}
