package com.olynk.waystonexppayment.recipe;

import com.olynk.waystonexppayment.fluid.FluidDeclaration;
import com.olynk.waystonexppayment.fluid.XpRate;
import com.olynk.waystonexppayment.registry.XppRegistry;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Data-driven statement that one fluid counts as experience and what a bucket of it is worth.
 *
 * <p>This is a recipe rather than config so the knowledge is datapack data: pack authors register it, packs override
 * each other by load order, and KubeJS can write it as
 * {@code e.custom({ type: 'waystone_xp_payment:xp_fluid', ... })}. It never matches a crafting input, so it occupies
 * no recipe slot and cannot conflict with other mods.
 *
 * <pre>
 * { "type": "waystone_xp_payment:xp_fluid", "fluid": "create_enchantment_industry:experience", "xp_per_bucket": 1000 }
 * { "type": "waystone_xp_payment:xp_fluid", "fluid": "some_mod:thin_xp", "xp_per_bucket": 0 }
 * </pre>
 */
public class XpFluidRecipe implements Recipe<RecipeInput> {

    private final ResourceLocation fluid;
    private final double xpPerBucket;

    public XpFluidRecipe(ResourceLocation fluid, double xpPerBucket) {
        if (xpPerBucket < 0) {
            throw new IllegalArgumentException("xp_per_bucket must not be negative, was " + xpPerBucket);
        }
        // Validating here (not at use time) turns a bad datapack into a load error instead of a surprise at teleport.
        if (xpPerBucket > 0) {
            XpRate.parse(xpPerBucket);
        }
        this.fluid = fluid;
        this.xpPerBucket = xpPerBucket;
    }

    public ResourceLocation fluid() {
        return fluid;
    }

    public double xpPerBucket() {
        return xpPerBucket;
    }

    /** {@code xp_per_bucket: 0} asks the table to forget this fluid, so a pack can cancel the builtin entry. */
    public boolean unregisters() {
        return xpPerBucket <= 0;
    }

    /**
     * Turns this recipe into one statement for the rate table, carrying the priority it was loaded with.
     *
     * @param packOrder position of the declaring pack in the enabled-pack list; higher wins
     */
    public FluidDeclaration toDeclaration(String recipeId, int packOrder, String packId) {
        XpRate rate = unregisters() ? null : XpRate.parse(xpPerBucket);
        return new FluidDeclaration(fluid.toString(), rate, packId + ": " + recipeId, packOrder, recipeId);
    }

    public boolean matchesFluid(FluidStack stack) {
        return !stack.isEmpty() && fluid.equals(BuiltInRegistries.FLUID.getKey(stack.getFluid()));
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
