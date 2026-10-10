package com.olynk.waystonexppayment.event;

import java.util.ArrayList;
import java.util.List;

import com.olynk.waystonexppayment.WaystoneXpPaymentMod;
import com.olynk.waystonexppayment.command.XppCommands;
import com.olynk.waystonexppayment.fluid.FluidRates;
import com.olynk.waystonexppayment.recipe.XpFluidRecipe;
import com.olynk.waystonexppayment.registry.XppRegistry;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Game-bus wiring: recipe-declared fluid rates, and the {@code /xpp} command.
 *
 * <p>Recipe declarations are the datapack/KubeJS half of "which fluid counts as experience"; they are re-read
 * whenever the server starts or a datapack reload finishes syncing, so a {@code /reload} picks up new ones.
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
        List<String> entries = new ArrayList<>();
        for (RecipeHolder<XpFluidRecipe> holder : server.getRecipeManager()
                .getAllRecipesFor(XppRegistry.XP_FLUID_RECIPE_TYPE.get())) {
            String entry = holder.value().toRateEntry();
            if (entry != null && !entry.isBlank()) {
                entries.add(entry);
            }
        }
        FluidRates.rebuild(entries);
        WaystoneXpPaymentMod.LOGGER.info("Loaded {} recipe-declared experience fluid rate(s): {}",
                entries.size(), entries);
    }
}
