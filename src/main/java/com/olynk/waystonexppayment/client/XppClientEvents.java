package com.olynk.waystonexppayment.client;

import com.olynk.waystonexppayment.WaystoneXpPaymentMod;
import com.olynk.waystonexppayment.registry.XppRegistry;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client-only wiring: which screen belongs to the tank menu. */
@EventBusSubscriber(modid = WaystoneXpPaymentMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class XppClientEvents {

    private XppClientEvents() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(XppRegistry.XP_PAYMENT_TANK_MENU.get(), XpPaymentTankScreen::new);
    }
}
