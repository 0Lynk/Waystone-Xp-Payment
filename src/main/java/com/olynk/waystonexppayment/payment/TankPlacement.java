package com.olynk.waystonexppayment.payment;

import net.blay09.mods.waystones.api.Waystone;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Where the payment tank has to sit for a given teleport source.
 *
 * <p>Waystones' waystones are double blocks ({@code WaystoneBlockBase} with a {@code DoubleBlockHalf}), and the
 * position recorded in the {@link Waystone} is not always the lower half - {@code WarpPortalBlock} even derives
 * its base as the upper half when placed. Taking {@code getPos().below()} therefore missed the slot directly
 * under a two-block-tall waystone, which is where players naturally put the tank.
 *
 * <p>Rule: if the block below the recorded position is the same waystone block, that is the lower half, so the
 * slot directly below the waystone is one further down. Single-block sources (warp plate, sharestone, ...) are
 * unaffected.
 */
public final class TankPlacement {

    private TankPlacement() {
    }

    /** The block position directly below the whole teleport source, tank or not. */
    public static BlockPos tankPosFor(Level level, Waystone waystone) {
        BlockPos pos = waystone.getPos();
        BlockState state = level.getBlockState(pos);
        BlockState below = level.getBlockState(pos.below());
        boolean lowerHalfFollows = !state.isAir() && below.getBlock() == state.getBlock();
        return lowerHalfFollows ? pos.below().below() : pos.below();
    }
}
