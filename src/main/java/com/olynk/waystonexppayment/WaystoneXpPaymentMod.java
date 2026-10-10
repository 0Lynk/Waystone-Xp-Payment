package com.olynk.waystonexppayment;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.olynk.waystonexppayment.config.XppConfig;
import com.olynk.waystonexppayment.event.XppTeleportHandler;
import com.olynk.waystonexppayment.registry.XppRegistry;

import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.waystones.api.event.WaystoneTeleportEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

/**
 * Entry point of the {@code waystone_xp_payment} mod: a Waystones addon that pays a teleport's XP cost from a
 * shared experience-fluid tank placed directly below the teleport source.
 *
 * <p>Design and decisions: {@code docs/design.md}.
 */
@Mod(WaystoneXpPaymentMod.MOD_ID)
public final class WaystoneXpPaymentMod {
    public static final String MOD_ID = "waystone_xp_payment";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WaystoneXpPaymentMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, XppConfig.SPEC);
        XppRegistry.register(modEventBus);

        // Balm events are plain subscriptions, not NeoForge bus listeners. Waystones (and therefore Balm) is a
        // hard dependency, so its own initialisation has already run by the time this constructor executes.
        Balm.getEvents().onEvent(WaystoneTeleportEvent.Pre.class, XppTeleportHandler::onTeleportPre);

        LOGGER.info("{} {} initialized: Waystones teleports can be paid from experience fluid tanks",
                MOD_ID, modContainer.getModInfo().getVersion());
    }
}
