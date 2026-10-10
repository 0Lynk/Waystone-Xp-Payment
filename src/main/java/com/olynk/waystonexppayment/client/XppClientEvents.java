package com.olynk.waystonexppayment.client;

import com.olynk.waystonexppayment.WaystoneXpPaymentMod;
import com.olynk.waystonexppayment.registry.XppRegistry;

import net.blay09.mods.waystones.client.requirement.RequirementClientRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client-only wiring: the tank screen, and the requirement renderer that keeps Waystones' cost icon honest. */
@EventBusSubscriber(modid = WaystoneXpPaymentMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class XppClientEvents {

    private XppClientEvents() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(XppRegistry.XP_PAYMENT_TANK_MENU.get(), XpPaymentTankScreen::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // Waystones registers its own renderers from its client setup; adding ours alongside them is enough,
        // because a renderer is looked up per requirement class and never replaced.
        RequirementClientRegistry.registerRenderer(ClientFluidPaymentRequirement.class,
                new ClientFluidPaymentRenderer());
    }
}
