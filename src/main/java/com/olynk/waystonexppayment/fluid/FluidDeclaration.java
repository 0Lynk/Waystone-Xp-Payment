package com.olynk.waystonexppayment.fluid;

import org.jetbrains.annotations.Nullable;

/**
 * One datapack statement about one fluid: either a rate, or a request to stop treating the fluid as experience.
 *
 * <p>Ordering is the whole point of this type. Datapacks override each other by load order, so the table is built by
 * applying declarations sorted by {@link #packOrder()} and then {@link #recipeId()}: the last one applied wins.
 */
public record FluidDeclaration(String fluidId, @Nullable XpRate rate, String source, int packOrder, String recipeId)
        implements Comparable<FluidDeclaration> {

    /** {@code xp_per_bucket: 0} - the fluid stops being experience even if a lower priority pack declared it. */
    public boolean isUnregister() {
        return rate == null;
    }

    @Override
    public int compareTo(FluidDeclaration other) {
        int byPack = Integer.compare(packOrder, other.packOrder);
        if (byPack != 0) {
            return byPack;
        }
        return recipeId.compareTo(other.recipeId);
    }
}
