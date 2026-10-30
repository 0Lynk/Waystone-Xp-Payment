package com.olynk.waystonexppayment.fluid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Runtime view over {@link FluidRateTable}: the fluids declared by {@code waystone_xp_payment:xp_fluid} recipes.
 *
 * <p>The table starts empty and is rebuilt on server start and on every datapack sync. An empty table means "nothing
 * is experience yet", never "everything is experience with the default rate": a payment path may not invent rates.
 */
public final class FluidRates {

    private static volatile FluidRateTable table = FluidRateTable.builder().build();

    private FluidRates() {
    }

    public static FluidRateTable table() {
        return table;
    }

    /** Applies declarations in priority order: the last writer of a fluid id wins. */
    public static void rebuild(Collection<FluidDeclaration> declarations) {
        List<FluidDeclaration> ordered = new ArrayList<>(declarations);
        ordered.sort(null);
        FluidRateTable.Builder builder = FluidRateTable.builder();
        for (FluidDeclaration declaration : ordered) {
            if (declaration.isUnregister()) {
                builder.unregister(declaration.fluidId());
            } else {
                builder.declare(declaration.fluidId(), declaration.rate(), declaration.source());
            }
        }
        table = builder.build();
    }

    /** Drops every declaration; used when a datapack reload leaves nothing declared. */
    public static void clear() {
        table = FluidRateTable.builder().build();
    }

    /**
     * Human-readable explanation of how (or whether) a stack counts as experience fluid.
     *
     * <p>Exists so {@code /waystonexppayment info} and the teleport log can answer "why is this fluid not
     * paying?" from the player's own machine instead of from speculation.
     */
    public static String describe(FluidStack stack) {
        if (stack.isEmpty()) {
            return "empty tank";
        }
        String id = idOf(stack);
        return table.resolve(id)
                .map(found -> id + " -> " + found.rate().describe() + " via " + found.source())
                .orElseGet(() -> id + " -> not registered as experience fluid");
    }

    public static String idOf(FluidStack stack) {
        return BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString();
    }

    public static Optional<XpRate> rateFor(FluidStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        return table.rateFor(idOf(stack));
    }

    public static boolean accepts(FluidStack stack) {
        return rateFor(stack).isPresent();
    }

    /** How much XP the whole stack is worth (rounded down). */
    public static int xpValue(FluidStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        return table.xpValue(idOf(stack), stack.getAmount());
    }
}
