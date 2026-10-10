package com.olynk.waystonexppayment.recipe;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.olynk.waystonexppayment.registry.XppRegistry;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Data-driven declaration that a fluid (by id or by tag) is experience and how many mB of it are worth 1 XP.
 *
 * <p>This is intentionally a recipe rather than (only) config: it makes the declaration datapack data and lets
 * pack authors register it with KubeJS as {@code e.custom({ type: 'waystone_xp_payment:xp_fluid', ... })}.
 * It never matches a crafting input, so it occupies no recipe slot and cannot conflict with other mods.
 *
 * <pre>
 * { "type": "waystone_xp_payment:xp_fluid", "fluid": "create_enchantment_industry:experience", "mb_per_xp": 1 }
 * { "type": "waystone_xp_payment:xp_fluid", "tag": "c:experience", "mb_per_xp": 1 }
 * </pre>
 */
public class XpFluidRecipe implements Recipe<RecipeInput> {

    private final Optional<ResourceLocation> fluid;
    private final Optional<TagKey<Fluid>> tag;
    private final int mbPerXp;

    public XpFluidRecipe(Optional<ResourceLocation> fluid, Optional<TagKey<Fluid>> tag, int mbPerXp) {
        this.fluid = fluid;
        this.tag = tag;
        this.mbPerXp = Math.max(1, mbPerXp);
    }

    public static XpFluidRecipe ofFluid(ResourceLocation fluid, int mbPerXp) {
        return new XpFluidRecipe(Optional.of(fluid), Optional.empty(), mbPerXp);
    }

    public static XpFluidRecipe ofTag(TagKey<Fluid> tag, int mbPerXp) {
        return new XpFluidRecipe(Optional.empty(), Optional.of(tag), mbPerXp);
    }

    public Optional<ResourceLocation> fluid() {
        return fluid;
    }

    public Optional<TagKey<Fluid>> tag() {
        return tag;
    }

    public int mbPerXp() {
        return mbPerXp;
    }

    public boolean matchesFluid(FluidStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (fluid.isPresent() && fluid.get().equals(BuiltInRegistries.FLUID.getKey(stack.getFluid()))) {
            return true;
        }
        return tag.isPresent() && stack.is(tag.get());
    }

    /** The equivalent {@code <key>=<rate>} entry, so recipe and config declarations share one lookup table. */
    @Nullable
    public String toRateEntry() {
        if (fluid.isPresent()) {
            return fluid.get() + "=" + mbPerXp;
        }
        return tag.map(value -> "#" + value.location() + "=" + mbPerXp).orElse(null);
    }

    // A registration entry, never a craftable recipe.

    @Override
    public boolean matches(RecipeInput input, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return false;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return XppRegistry.XP_FLUID_RECIPE_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return XppRegistry.XP_FLUID_RECIPE_TYPE.get();
    }
}
