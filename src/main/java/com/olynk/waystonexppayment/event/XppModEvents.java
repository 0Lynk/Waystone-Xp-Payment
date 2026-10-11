package com.olynk.waystonexppayment.event;

import com.olynk.waystonexppayment.WaystoneXpPaymentMod;
import com.olynk.waystonexppayment.client.XppClientState;
import com.olynk.waystonexppayment.config.XppConfig;
import com.olynk.waystonexppayment.config.XppSettings;
import com.olynk.waystonexppayment.fluid.FluidRates;
import com.olynk.waystonexppayment.net.XppAccessPayload;
import com.olynk.waystonexppayment.net.XppMembersPayload;
import com.olynk.waystonexppayment.net.XppServerHandler;
import com.olynk.waystonexppayment.net.XppStatePayload;
import com.olynk.waystonexppayment.registry.XppRegistry;

import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Mod-bus wiring: capabilities, creative tab entry and config reload. */
@EventBusSubscriber(modid = WaystoneXpPaymentMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class XppModEvents {

    private XppModEvents() {
    }

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // The cross-mod contract for fluids: any tech mod's pipes/pumps fill the tank through this capability.
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, XppRegistry.XP_PAYMENT_TANK_BE.get(),
                (blockEntity, side) -> blockEntity.getFluidHandler(side));
    }

    @SubscribeEvent
    public static void buildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(XppRegistry.XP_PAYMENT_TANK_ITEM.get());
        }
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // Bumped from "1": both the payload set and the Action enum changed, and a version mismatch is refused
        // during the handshake, which is far easier to diagnose than a byte stream that decodes into nonsense.
        PayloadRegistrar registrar = event.registrar("2");
        // The client class is only touched inside the handler, which never runs on a dedicated server.
        registrar.playToClient(XppStatePayload.TYPE, XppStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> XppClientState.accept(payload)));
        registrar.playToClient(XppMembersPayload.TYPE, XppMembersPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> XppClientState.acceptMembers(payload)));
        registrar.playToServer(XppAccessPayload.TYPE, XppAccessPayload.STREAM_CODEC, XppServerHandler::handle);
    }

    @SubscribeEvent
    public static void onConfigLoading(ModConfigEvent.Loading event) {
        applyConfig(event);
    }

    @SubscribeEvent
    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        applyConfig(event);
    }

    private static void applyConfig(ModConfigEvent event) {
        if (event.getConfig().getSpec() != XppConfig.SPEC) {
            return;
        }
        XppSettings.reloadFromConfig();
        // Recipe-declared rates are re-added on server start / datapack sync; here the config-only view is enough.
        FluidRates.rebuildFromConfig();
    }
}
