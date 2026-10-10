package com.olynk.waystonexppayment.payment;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.blay09.mods.waystones.api.requirement.WarpRequirement;
import net.blay09.mods.waystones.requirement.CombinedRequirement;
import net.blay09.mods.waystones.requirement.ExperienceLevelRequirement;
import net.blay09.mods.waystones.requirement.ExperiencePointsRequirement;

/**
 * Walks an already resolved Waystones requirement tree and reads the XP cost out of it.
 *
 * <p>Deliberately does not recompute the cost: the tree Waystones built from
 * {@code teleports.warpRequirements} already applied every modifier
 * ({@code scaled_add_xp_cost} / {@code min_xp_cost} / {@code max_xp_cost} / {@code multiply_xp_cost}),
 * so reading it keeps those authoritative.
 */
public final class XpRequirementScanner {

    private XpRequirementScanner() {
    }

    /** Total XP the resolved requirement tree asks for. */
    public static int xpCost(WarpRequirement requirement) {
        int[] total = {0};
        walk(requirement, current -> {
            if (current instanceof ExperiencePointsRequirement points) {
                total[0] += Math.max(0, points.getPoints());
            }
            return false;
        });
        return total[0];
    }

    /** True when the tree contains an XP *level* requirement, which this mod does not cover (v1). */
    public static boolean hasLevelRequirement(WarpRequirement requirement) {
        return walk(requirement, current -> current instanceof ExperienceLevelRequirement);
    }

    /**
     * A copy of the requirement tree with every XP-point requirement removed, so the tank can take over that
     * part of the payment. Other requirements (cooldowns, item costs, ...) are preserved as-is.
     *
     * @return the stripped tree, {@code null} when nothing is left to pay, or the input instance when nothing
     *         was removed
     */
    @Nullable
    public static WarpRequirement withoutXpCost(@Nullable WarpRequirement requirement) {
        if (requirement == null) {
            return null;
        }
        if (requirement instanceof ExperiencePointsRequirement) {
            return null;
        }
        if (requirement instanceof CombinedRequirement combined) {
            List<WarpRequirement> kept = new ArrayList<>();
            boolean changed = false;
            for (WarpRequirement child : combined.getRequirements()) {
                WarpRequirement stripped = withoutXpCost(child);
                if (stripped == null) {
                    changed = true;
                    continue;
                }
                if (stripped != child) {
                    changed = true;
                }
                kept.add(stripped);
            }
            if (!changed) {
                return requirement;
            }
            return kept.isEmpty() ? null : new CombinedRequirement(kept);
        }
        return requirement;
    }

    /**
     * Depth-first walk over the tree, invoking {@code action} on every node.
     *
     * @return true as soon as {@code action} returned true, false otherwise
     */
    private static boolean walk(WarpRequirement requirement, Predicate<WarpRequirement> action) {
        if (requirement == null || requirement.isEmpty()) {
            return false;
        }
        Deque<WarpRequirement> queue = new ArrayDeque<>();
        Set<WarpRequirement> seen = new HashSet<>();
        queue.add(requirement);
        while (!queue.isEmpty()) {
            WarpRequirement current = queue.poll();
            if (current == null || !seen.add(current)) {
                continue;
            }
            if (action.test(current)) {
                return true;
            }
            if (current instanceof CombinedRequirement combined) {
                queue.addAll(combined.getRequirements());
            }
        }
        return false;
    }
}
