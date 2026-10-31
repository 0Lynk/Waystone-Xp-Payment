package com.olynk.waystonexppayment.fluid;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * The one place that decides what "the same fluid" means across its still and flowing forms.
 *
 * <p>Minecraft registers a flowing fluid as a separate id, so a pipe fills a tank with
 * {@code create_enchantment_industry:flowing_experience} while a bucket pours
 * {@code create_enchantment_industry:experience}. Both are the same substance, and both have to price, store and
 * display identically - otherwise a tank filled by pipe looks like a tank full of something nobody declared.
 *
 * <p>The answer comes from the fluid object, never from the id string: every {@link FlowingFluid} knows its own still
 * form through {@link FlowingFluid#getSource()}. String guessing survives only as a fallback for registries that do
 * not use {@code FlowingFluid} at all (see {@link FluidRateTable#counterpartId}).
 */
public final class FluidIds {

    private FluidIds() {
    }

    /**
     * The still form of a fluid, or the fluid itself when it has none.
     *
     * <p>Single-form fluids (Industrial Foregoing's essence, for one) are left untouched, which is what makes this
     * safe for mods that never register a pair.
     */
    public static Fluid canonical(Fluid fluid) {
        if (!(fluid instanceof FlowingFluid flowing)) {
            return fluid;
        }
        Fluid source = flowing.getSource();
        return hasDistinctStillForm(true, source != null) ? source : fluid;
    }

    /** The same stack restamped onto its still form; the original instance when there is nothing to change. */
    public static FluidStack canonical(FluidStack stack) {
        if (stack.isEmpty()) {
            return stack;
        }
        Fluid still = canonical(stack.getFluid());
        if (still == stack.getFluid()) {
            return stack;
        }
        FluidStack restamped = new FluidStack(still, stack.getAmount());
        restamped.applyComponents(stack.getComponentsPatch());
        return restamped;
    }

    /** Registry id of the still form - the id that the rate table, the index and the screen all agree on. */
    public static String idOf(FluidStack stack) {
        return BuiltInRegistries.FLUID.getKey(canonical(stack.getFluid())).toString();
    }

    /**
     * Whether a fluid needs restamping.
     *
     * <p>Split out so the decision is testable without a game registry: it is the only branch in this class, and
     * getting it wrong means a tank that quietly holds a fluid nobody declared.
     */
    static boolean hasDistinctStillForm(boolean flowingVariant, boolean stillFormKnown) {
        return flowingVariant && stillFormKnown;
    }
}
