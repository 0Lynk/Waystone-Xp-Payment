package com.olynk.waystonexppayment.payment;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Where the payment tank has to sit for a given teleport source.
 *
 * <p>Waystones' waystones are double blocks ({@code WaystoneBlockBase} with a {@code DoubleBlockHalf}), and the
 * position recorded in the {@code Waystone} is not always the lower half - {@code WarpPortalBlock} even derives
 * its base as the upper half when placed. Taking {@code getPos().below()} therefore missed the slot directly
 * under a two-block-tall waystone, which is where players naturally put the tank.
 *
 * <p>Rule: if the block below the recorded position is the same waystone block, that is the lower half, so the
 * slot directly below the waystone is one further down. Single-block sources (warp plate, sharestone, ...) are
 * unaffected.
 *
 * <p>Reading that rule needs the two block states, and a teleport routinely asks about a destination whose chunk
 * is not resident: a client reads the empty chunk's {@code VOID_AIR} for it, and the server would load the chunk,
 * which has to stay inside the caller's load budget. Without that budget the shape is simply not knowable, and
 * "is the block below the same waystone" answered {@code false} - so the tank was looked for one block too high
 * and the destination tank never paid. Hence two layers: an observation-free
 * {@link #candidateTankPositions(BlockPos, RecordedHalf)} decision that unit tests can drive directly, and a
 * {@link Level}-aware wrapper that only has to report which case it is in.
 */
public final class TankPlacement {

    /** What the recorded waystone position is, as far as the caller could observe. */
    public enum RecordedHalf {
        /** The block below the recorded position is the same waystone block: the tank is two blocks down. */
        UPPER_HALF,
        /** Single-block source, or the recorded position already is the lower half: the tank is one block down. */
        LOWER_OR_SINGLE,
        /**
         * The chunk holding the waystone is not resident here, so its shape is unknown and both slots stay in
         * play. Never collapsed to {@link #LOWER_OR_SINGLE}: that substitution is the bug, not a fallback.
         */
        UNKNOWN
    }

    private TankPlacement() {
    }

    /**
     * Every position a tank under this waystone can occupy, most likely first.
     *
     * <p>Split from the {@link Level} overload so the decision can be unit tested without a running world:
     * {@link RecordedHalf} carries the only fact the block states were consulted for.
     *
     * @param waystonePos the position Waystones recorded for the waystone; may be either half
     * @param half what the recorded position turned out to be, {@link RecordedHalf#UNKNOWN} when unobservable
     * @return never empty; one position once the shape is known, both possible ones in preference order when it
     *         is not, and never the same position twice
     */
    public static List<BlockPos> candidateTankPositions(BlockPos waystonePos, RecordedHalf half) {
        BlockPos below = waystonePos.below();
        BlockPos belowBoth = below.below();
        if (half == RecordedHalf.UPPER_HALF) {
            return List.of(belowBoth);
        }
        if (half == RecordedHalf.LOWER_OR_SINGLE) {
            return List.of(below);
        }
        // Both stay in play, and both get probed: a repeat would spend two chunk-load budget slots on the same
        // chunk, so the union is deduplicated even though the two offsets cannot collide today.
        Set<BlockPos> ordered = new LinkedHashSet<>();
        ordered.add(below);
        ordered.add(belowBoth);
        return List.copyOf(ordered);
    }

    /**
     * The same candidates for a live waystone, from whatever this side can currently observe.
     *
     * @return never empty
     */
    public static List<BlockPos> candidateTankPositions(Level level, BlockPos waystonePos) {
        return candidateTankPositions(waystonePos, recordedHalf(level, waystonePos));
    }

    /**
     * What the recorded position is, as far as this side can tell.
     *
     * <p>{@code isLoaded} is the probe, not {@code getBlockState}: on the server the latter loads the chunk
     * outright, which has to stay inside the caller's load budget, and on a client that does not have the chunk
     * it reports air for every position in it.
     */
    private static RecordedHalf recordedHalf(Level level, BlockPos waystonePos) {
        if (!level.isLoaded(waystonePos)) {
            return RecordedHalf.UNKNOWN;
        }
        BlockState state = level.getBlockState(waystonePos);
        BlockState below = level.getBlockState(waystonePos.below());
        return !state.isAir() && below.getBlock() == state.getBlock()
                ? RecordedHalf.UPPER_HALF
                : RecordedHalf.LOWER_OR_SINGLE;
    }
}
