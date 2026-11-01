package com.olynk.waystonexppayment.registry;

import java.util.function.Supplier;

import com.olynk.waystonexppayment.WaystoneXpPaymentMod;
import com.olynk.waystonexppayment.block.XpPaymentTankBlock;
import com.olynk.waystonexppayment.block.entity.XpPaymentTankBlockEntity;
import com.olynk.waystonexppayment.menu.XpPaymentTankMenu;
import com.olynk.waystonexppayment.recipe.XpFluidRecipe;
import com.olynk.waystonexppayment.recipe.XpFluidRecipeSerializer;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** All registry objects of this mod in one place. */
public final class XppRegistry {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(WaystoneXpPaymentMod.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(WaystoneXpPaymentMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, WaystoneXpPaymentMod.MOD_ID);
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, WaystoneXpPaymentMod.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, WaystoneXpPaymentMod.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, WaystoneXpPaymentMod.MOD_ID);

    // No requiresCorrectToolForDrops(): the tank is where a player parks their experience fluid, so a swing with
    // the wrong tool (or a bare hand) must still return the block instead of deleting the pool inside it.
    public static final DeferredBlock<XpPaymentTankBlock> XP_PAYMENT_TANK = BLOCKS.register("xp_payment_tank",
            () -> new XpPaymentTankBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .sound(SoundType.METAL)
                    // Without this the tank counts as a full cube: neighbours drop the faces that touch it, so
                    // looking down through the glass floor showed the void instead of the grass underneath.
                    .noOcclusion()));

    public static final DeferredItem<BlockItem> XP_PAYMENT_TANK_ITEM =
            ITEMS.registerSimpleBlockItem("xp_payment_tank", XP_PAYMENT_TANK);

    public static final Supplier<BlockEntityType<XpPaymentTankBlockEntity>> XP_PAYMENT_TANK_BE =
            BLOCK_ENTITY_TYPES.register("xp_payment_tank",
                    () -> BlockEntityType.Builder.of(XpPaymentTankBlockEntity::new, XP_PAYMENT_TANK.get()).build(null));

    public static final Supplier<RecipeType<XpFluidRecipe>> XP_FLUID_RECIPE_TYPE = RECIPE_TYPES.register("xp_fluid",
            () -> new RecipeType<XpFluidRecipe>() {
                @Override
                public String toString() {
                    return WaystoneXpPaymentMod.MOD_ID + ":xp_fluid";
                }
            });

    public static final Supplier<XpFluidRecipeSerializer> XP_FLUID_RECIPE_SERIALIZER =
            RECIPE_SERIALIZERS.register("xp_fluid", XpFluidRecipeSerializer::new);

    /** No extra open-screen data is needed: the tank position arrives with the state payload. */
    public static final Supplier<MenuType<XpPaymentTankMenu>> XP_PAYMENT_TANK_MENU =
            MENUS.register("xp_payment_tank",
                    () -> new MenuType<>(XpPaymentTankMenu::new, FeatureFlags.VANILLA_SET));

    private XppRegistry() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        RECIPE_TYPES.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
        MENUS.register(modEventBus);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(WaystoneXpPaymentMod.MOD_ID, path);
    }
}
