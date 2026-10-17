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
 * <p>WHY one helper: the access rows and the team member rows both render a stored UUID, and the fallback for a
 * player who logged off was written out twice - the team list already went profile cache to short id, while the
 * access list skipped the cache and printed the raw id. The cache is the only source that still knows the name
 * after logout, so the whole chain lives here once and both lists ask the same question.
 */
public final class PlayerNames {
    private PlayerNames() {
    }

    /**
     * The name the server remembers for {@code id}, or {@code null} when it has no record of them.
     *
     * <p>Null server and null cache are both possible (no profile repository configured); nothing here may
     * depend on either existing. A caller that gets {@code null} hands it to {@link #label}, which ends the
     * chain at {@link #shortId(UUID)}.
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
     * Row label for a stored id: a live name, then one the profile cache remembers, then a short id.
     *
     * <p>WHY the two names arrive as arguments: this is the rule the access list and the team member list have to
     * share, and neither of them has a running server to exercise it against in a test.
     */
    public static String label(UUID id, @Nullable String onlineName, @Nullable String rememberedName) {
        if (onlineName != null && !onlineName.isBlank()) {
            return onlineName;
        }
        if (rememberedName != null && !rememberedName.isBlank()) {
            return rememberedName;
        }
        return shortId(id);
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
