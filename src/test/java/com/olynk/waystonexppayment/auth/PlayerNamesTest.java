package com.olynk.waystonexppayment.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * The name chain the tank screen rides on: the short id, and the precedence between a live name, a remembered
 * one and that short id.
 *
 * <p>{@link PlayerNames#remembered} needs a live {@code MinecraftServer}, so the cache lookup itself is covered
 * by the code path only; its null-server branch and the rest of the chain are pure and live here.
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

    // The reported bug: a member who logged off lost their name and the row showed the raw 36-character UUID.
    // The order of the three tests below is the fix.
    @Test
    void anOnlineNameWins() {
        assertEquals("Dr_Dogey", PlayerNames.label(ID, "Dr_Dogey", "DrDogey"));
    }

    @Test
    void theRememberedNameCoversALoggedOffPlayer() {
        assertEquals("DrDogey", PlayerNames.label(ID, null, "DrDogey"));
    }

    @Test
    void anIdNobodyKnowsFallsBackToItsShortForm() {
        assertEquals("6d1c4a6f", PlayerNames.label(ID, null, null));
        assertEquals("6d1c4a6f", PlayerNames.label(ID, "  ", " "));
    }
}
