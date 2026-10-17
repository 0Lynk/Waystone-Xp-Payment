package com.olynk.waystonexppayment.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Precedence of the row label the tank screen prints for a stored player id.
 *
 * <p>The reported bug: a member who logged off lost their name and the row showed the raw 36-character UUID.
 * The fix is the order tested here - live name, then the name the profile cache remembers, then a short id.
 */
class XppStateLabelTest {
    private static final UUID ID = UUID.fromString("6d1c4a6f-2e4b-4f0f-9a3e-1c2b3a4d5e6f");

    @Test
    void anOnlineNameWins() {
        assertEquals("Dr_Dogey", XppStatePayload.labelFor(ID, "Dr_Dogey", "DrDogey"));
    }

    @Test
    void theRememberedNameCoversALoggedOffMember() {
        assertEquals("DrDogey", XppStatePayload.labelFor(ID, null, "DrDogey"));
    }

    @Test
    void anIdNobodyKnowsFallsBackToItsShortForm() {
        assertEquals("6d1c4a6f", XppStatePayload.labelFor(ID, null, null));
        assertEquals("6d1c4a6f", XppStatePayload.labelFor(ID, "  ", " "));
    }
}
