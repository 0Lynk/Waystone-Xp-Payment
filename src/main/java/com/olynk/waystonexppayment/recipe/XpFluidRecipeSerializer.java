package com.olynk.waystonexppayment.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * Serializer for {@link XpFluidRecipe}: datapack JSON plus a hand-written stream codec.
 *
 * <p>Both fields are required on purpose. An old pack that still writes {@code mb_per_xp} (or {@code tag}) fails to
 * parse and is dropped by the recipe manager with an error, which is louder than silently charging the wrong rate.
 */
public class XpFluidRecipeSerializer implements RecipeSerializer<XpFluidRecipe> {

    public static final MapCodec<XpFluidRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    ResourceLocation.CODEC.fieldOf("fluid").forGetter(XpFluidRecipe::fluid),
                    Codec.DOUBLE.fieldOf("xp_per_bucket").forGetter(XpFluidRecipe::xpPerBucket))
            .apply(instance, XpFluidRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, XpFluidRecipe> STREAM_CODEC = StreamCodec.of(
            (buffer, recipe) -> {
                buffer.writeResourceLocation(recipe.fluid());
                buffer.writeDouble(recipe.xpPerBucket());
            },
            buffer -> new XpFluidRecipe(buffer.readResourceLocation(), buffer.readDouble()));

    @Override
    public MapCodec<XpFluidRecipe> codec() {
        return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, XpFluidRecipe> streamCodec() {
        return STREAM_CODEC;
    }
}
