package com.olynk.waystonexppayment.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * The two halves of the name chain that need no running server: no repository, and the last-resort label.
 *
 * <p>{@link PlayerNames#remembered} needs a live {@code MinecraftServer}, so the cache lookup itself is covered
 * by the code path only; its null-server branch and the short id are pure and live here.
 */
class PlayerNamesTest {
    private static final UUID ID = UUID.fromString("6d1c4a6f-2e4b-4f0f-9a3e-1c2b3a4d5e6f");

    @Test
    void aMissingServerHasNoRememberedName() {
        assertNull(PlayerNames.remembered(null, ID));
    }

    @Test
    void theShortIdIsTheFirstEightCharacters() {
        assertEquals("6d1c4a6f", PlayerNames.shortId(ID));
    }
}
