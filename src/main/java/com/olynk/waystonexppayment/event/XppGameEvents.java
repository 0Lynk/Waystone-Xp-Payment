package com.olynk.waystonexppayment.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.olynk.waystonexppayment.WaystoneXpPaymentMod;
import com.olynk.waystonexppayment.command.XppCommands;
import com.olynk.waystonexppayment.fluid.FluidDeclaration;
import com.olynk.waystonexppayment.fluid.FluidRates;
import com.olynk.waystonexppayment.recipe.XpFluidRecipe;
import com.olynk.waystonexppayment.registry.XppRegistry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Game-bus wiring: datapack-declared fluid rates, and the {@code /xpp} command.
 *
 * <p>Declarations are the datapack/KubeJS half of "which fluid counts as experience". They are re-read whenever the
 * server starts or a datapack reload finishes syncing, so a {@code /reload} picks up new ones - and only then, because
 * a config reload can no longer change rates at all.
 */
@EventBusSubscriber(modid = WaystoneXpPaymentMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class XppGameEvents {

    private XppGameEvents() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        rebuildRates(event.getServer());
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        rebuildRates(event.getPlayerList().getServer());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        XppCommands.register(event.getDispatcher());
    }

    private static void rebuildRates(MinecraftServer server) {
        ResourceManager resources = server.getResourceManager();
        Map<String, Integer> packOrder = packOrder(server);
        List<FluidDeclaration> declarations = new ArrayList<>();
        for (RecipeHolder<XpFluidRecipe> holder : server.getRecipeManager()
                .getAllRecipesFor(XppRegistry.XP_FLUID_RECIPE_TYPE.get())) {
            ResourceLocation id = holder.id();
            String pack = sourcePackOf(resources, id);
            declarations.add(holder.value()
                    .toDeclaration(id.toString(), packOrder.getOrDefault(pack, 0), pack));
        }
        FluidRates.rebuild(declarations);
        WaystoneXpPaymentMod.LOGGER.info("Loaded {} experience fluid declaration(s), {} fluid(s) in the table",
                declarations.size(), FluidRates.table().declaredCount());
        warnAboutConflicts(declarations);
    }

    /** Position of each enabled pack in the list datapacks load in; a later pack overrides an earlier one. */
    private static Map<String, Integer> packOrder(MinecraftServer server) {
        Map<String, Integer> order = new HashMap<>();
        List<Pack> selected = List.copyOf(server.getPackRepository().getSelectedPacks());
        for (int i = 0; i < selected.size(); i++) {
            order.put(selected.get(i).getId(), i);
        }
        return order;
    }

    /** The pack a recipe file came from, so two packs claiming the same fluid can be ordered by priority. */
    private static String sourcePackOf(ResourceManager resources, ResourceLocation recipeId) {
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(recipeId.getNamespace(),
                PackType.SERVER_DATA.getDirectory() + "/" + "recipe/" + recipeId.getPath() + ".json");
        return resources.getResource(file).map(Resource::sourcePackId).orElse("unknown");
    }

    private static void warnAboutConflicts(List<FluidDeclaration> declarations) {
        Map<String, List<FluidDeclaration>> byFluid = new LinkedHashMap<>();
        for (FluidDeclaration declaration : declarations) {
            byFluid.computeIfAbsent(declaration.fluidId(), key -> new ArrayList<>()).add(declaration);
        }
        byFluid.forEach((fluid, claims) -> {
            if (claims.size() > 1) {
                WaystoneXpPaymentMod.LOGGER.warn("{} is declared {} times ({}); the last one wins by pack order",
                        fluid, claims.size(), claims.stream().map(FluidDeclaration::source).toList());
            }
        });
    }
}
