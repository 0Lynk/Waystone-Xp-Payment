package com.olynk.waystonexppayment.auth;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.GameProfileCache;

/**
 * Names for a UUID the server has no live player object for.
 *
 * <p>WHY one helper: the access rows and the team member rows both render a stored UUID, and both printed the
 * raw id the moment its owner logged off. The profile cache is the only source that still knows the name after
 * logout, so every row asks this question through the same place instead of each deciding its own fallback.
 */
public final class PlayerNames {
    private PlayerNames() {
    }

    /**
     * The name the server remembers for {@code id}, or {@code null} when it has no record of them.
     *
     * <p>Null server and null cache are both possible (no profile repository configured); nothing here may
     * depend on either existing, and a caller that gets {@code null} falls back to {@link #shortId(UUID)}.
     */
    @Nullable
    public static String remembered(@Nullable MinecraftServer server, UUID id) {
        if (server == null) {
            return null;
        }
        GameProfileCache cache = server.getProfileCache();
        if (cache == null) {
            return null;
        }
        Optional<GameProfile> profile = cache.get(id);
        return profile.map(GameProfile::getName).filter(name -> !name.isBlank()).orElse(null);
    }

    /**
     * Last resort label: the first eight characters of the id.
     *
     * <p>Shorter than the full UUID so a row stays readable and its tooltip still identifies it exactly;
     * short enough that {@code XppWireLimits} never truncates it mid-name.
     */
    public static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }
}
