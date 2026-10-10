package com.olynk.waystonexppayment.auth;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.olynk.waystonexppayment.WaystoneXpPaymentMod;

import net.neoforged.fml.ModList;

/** Picks the team resolver once, based on which mods are loaded. */
public final class TeamResolvers {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile TeamResolver resolver;

    private TeamResolvers() {
    }

    public static TeamResolver get() {
        TeamResolver current = resolver;
        if (current == null) {
            synchronized (TeamResolvers.class) {
                current = resolver;
                if (current == null) {
                    current = load();
                    resolver = current;
                }
            }
        }
        return current;
    }

    private static TeamResolver load() {
        if (!ModList.get().isLoaded("ftbteams")) {
            LOGGER.info("[{}] FTB Teams not installed, team-based tank access disabled",
                    WaystoneXpPaymentMod.MOD_ID);
            return TeamResolver.NONE;
        }
        try {
            TeamResolver ftb = FtbTeamsResolver.INSTANCE;
            LOGGER.info("[{}] FTB Teams detected, team-based tank access enabled", WaystoneXpPaymentMod.MOD_ID);
            return ftb;
        } catch (Throwable t) {
            // LinkageError / NoClassDefFoundError / API drift: never break the game over a soft dependency.
            LOGGER.warn("[{}] FTB Teams is installed but its API could not be used, team-based tank access disabled",
                    WaystoneXpPaymentMod.MOD_ID, t);
            return TeamResolver.NONE;
        }
    }
}
