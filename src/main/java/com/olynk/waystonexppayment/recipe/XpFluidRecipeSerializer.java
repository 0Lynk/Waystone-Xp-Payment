package com.olynk.waystonexppayment.recipe;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.material.Fluid;

/** Serializer for {@link XpFluidRecipe}: datapack JSON plus a hand-written stream codec. */
public class XpFluidRecipeSerializer implements RecipeSerializer<XpFluidRecipe> {

    public static final MapCodec<XpFluidRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("fluid").forGetter(XpFluidRecipe::fluid),
                    TagKey.codec(Registries.FLUID).optionalFieldOf("tag").forGetter(XpFluidRecipe::tag),
                    Codec.INT.optionalFieldOf("mb_per_xp", 1).forGetter(XpFluidRecipe::mbPerXp))
            .apply(instance, XpFluidRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, XpFluidRecipe> STREAM_CODEC = StreamCodec.of(
            (buffer, recipe) -> {
                buffer.writeBoolean(recipe.fluid().isPresent());
                recipe.fluid().ifPresent(buffer::writeResourceLocation);
                buffer.writeBoolean(recipe.tag().isPresent());
                recipe.tag().ifPresent(tag -> buffer.writeResourceLocation(tag.location()));
                buffer.writeVarInt(recipe.mbPerXp());
            },
            buffer -> {
                Optional<ResourceLocation> fluid = buffer.readBoolean()
                        ? Optional.of(buffer.readResourceLocation())
                        : Optional.empty();
                Optional<TagKey<Fluid>> tag = buffer.readBoolean()
                        ? Optional.of(TagKey.create(Registries.FLUID, buffer.readResourceLocation()))
                        : Optional.empty();
                return new XpFluidRecipe(fluid, tag, buffer.readVarInt());
            });

    @Override
    public MapCodec<XpFluidRecipe> codec() {
        return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, XpFluidRecipe> streamCodec() {
        return STREAM_CODEC;
    }
}
