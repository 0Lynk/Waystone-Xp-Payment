package com.olynk.waystonexppayment.client;

import com.olynk.waystonexppayment.net.XppStatePayload;

import net.minecraft.client.Minecraft;

/** Client-side cache of the last tank snapshot, and its delivery to the open screen. */
public final class XppClientState {

    private static volatile XppStatePayload latest;

    private XppClientState() {
    }

    public static void accept(XppStatePayload payload) {
        latest = payload;
        if (Minecraft.getInstance().screen instanceof XpPaymentTankScreen screen) {
            screen.onState(payload);
        }
    }

    public static XppStatePayload latest() {
        return latest;
    }
}
