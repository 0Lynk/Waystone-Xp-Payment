package com.olynk.waystonexppayment.client;

import com.olynk.waystonexppayment.net.XppMembersPayload;
import com.olynk.waystonexppayment.net.XppStatePayload;
import com.olynk.waystonexppayment.net.XppTankHintPayload;

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

    /**
     * Delivers a member answer to the open screen.
     *
     * <p>Not cached like the snapshot: membership is only ever displayed while the member panel is open, and a
     * stored copy would go stale in a way nothing would notice.
     */
    public static void acceptMembers(XppMembersPayload payload) {
        if (Minecraft.getInstance().screen instanceof XpPaymentTankScreen screen) {
            screen.onMembers(payload);
        }
    }

    /** Feeds one server answer about tanks this client cannot see into {@link XppTankHints}. */
    public static void acceptHints(XppTankHintPayload payload) {
        XppTankHints.accept(payload);
    }

    public static XppStatePayload latest() {
        return latest;
    }
}
