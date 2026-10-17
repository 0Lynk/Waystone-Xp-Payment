package com.olynk.waystonexppayment.client;

import com.olynk.waystonexppayment.WaystoneXpPaymentMod;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Game-bus client hooks for the tank-hint queue.
 *
 * <p>Separate from {@link XppClientEvents} because that one is a mod-bus subscriber: these two events fire per
 * tick and per world, and a mod-bus class would never receive them.
 */
@EventBusSubscriber(modid = WaystoneXpPaymentMod.MOD_ID, value = Dist.CLIENT)
public final class XppClientTickEvents {

    private XppClientTickEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // One query per tick at most, drained from whatever the requirement preview queued.
        XppTankHints.flush();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // Answers about one world's tanks are not answers about the next world's.
        XppTankHints.clear();
        XppClientTankRegistry.clear();
    }
}
