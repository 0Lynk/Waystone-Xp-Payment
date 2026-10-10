package com.olynk.waystonexppayment.fluid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import com.olynk.waystonexppayment.config.XppConfig;
import com.olynk.waystonexppayment.config.XppSettings;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Runtime view over {@link FluidRateTable}: the union of the config list, the fluids declared by
 * {@code waystone_xp_payment:xp_fluid} recipes, and the fallback rate.
 */
public final class FluidRates {

    private static volatile FluidRateTable table = build(XppConfig.DEFAULT_FLUID_CONVERSIONS);

    private FluidRates() {
    }

    public static FluidRateTable table() {
        return table;
    }

    /** Rebuilds the table from the current config plus the given recipe-declared entries. */
    public static void rebuild(Collection<String> recipeEntries) {
        List<String> entries = new ArrayList<>(configEntries());
        entries.addAll(recipeEntries);
        table = build(entries);
    }

    /** Rebuilds from the config only (used when no server/recipe manager is available). */
    public static void rebuildFromConfig() {
        table = build(configEntries());
    }

    private static List<String> configEntries() {
        try {
            return List.copyOf(XppConfig.FLUID_CONVERSIONS.get());
        } catch (IllegalStateException e) {
            return XppConfig.DEFAULT_FLUID_CONVERSIONS;
        }
    }

    private static FluidRateTable build(Collection<String> entries) {
        XppSettings settings = XppSettings.current();
        return FluidRateTable.builder()
                .fallbackMbPerXp(settings.mbPerXp())
                .acceptUndeclared(settings.acceptUndeclaredFluids())
                .acceptExperienceNamedFluids(settings.acceptExperienceNamedFluids())
                .declareAll(entries)
                .build();
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
        List<String> tags = matchingTags(stack);
        Optional<FluidRateTable.Resolution> resolution = table.resolve(id, tags);
        return resolution
                .map(found -> id + " -> " + found.mbPerXp() + " mB/XP via " + found.source() + " (tags: " + tags + ")")
                .orElseGet(() -> id + " -> NOT accepted (tags: " + tags + ")");
    }

    public static String idOf(FluidStack stack) {
        return BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString();
    }

    /** Declared tags that the given stack matches (as written in the config, without the {@code #}). */
    public static List<String> matchingTags(FluidStack stack) {
        List<String> matches = new ArrayList<>();
        for (String tag : table.declaredTags()) {
            if (stack.is(TagKey.create(Registries.FLUID, ResourceLocation.parse(tag)))) {
                matches.add(tag);
            }
        }
        return matches;
    }

    public static OptionalInt rateFor(FluidStack stack) {
        if (stack.isEmpty()) {
            return OptionalInt.empty();
        }
        return table.rateFor(idOf(stack), matchingTags(stack));
    }

    public static boolean accepts(FluidStack stack) {
        return rateFor(stack).isPresent();
    }

    /** mB per XP for this stack, falling back to the configured default. */
    public static int mbPerXp(FluidStack stack) {
        OptionalInt rate = rateFor(stack);
        return rate.isPresent() ? rate.getAsInt() : table.fallbackMbPerXp();
    }

    /** How much XP the whole stack is worth (rounded down). */
    public static int xpValue(FluidStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        return table.xpValue(idOf(stack), matchingTags(stack), stack.getAmount());
    }
}
