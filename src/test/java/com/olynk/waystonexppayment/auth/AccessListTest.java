package com.olynk.waystonexppayment.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;

/**
 * NBT round-trip for the access list, including the entry that fails to parse.
 *
 * <p>Regression for the 2026-10-16 finding: one truncated int array used to clear the whole set, so a tank that
 * had been shared with several players came back allowing nobody.
 */
class AccessListTest {

    private static final UUID GOOD = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID TEAM = UUID.fromString("66666666-7777-8888-9999-000000000000");

    @Test
    void oneMalformedEntryDoesNotDropTheRestOfTheList() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("whitelist", true);
        ListTag players = new ListTag();
        players.add(NbtUtils.createUUID(GOOD));
        players.add(new IntArrayTag(new int[] {1, 2, 3}));
        tag.put("playerUuids", players);

        AccessList loaded = AccessList.load(tag);

        assertTrue(loaded.isPlayerListed(GOOD), "the good UUID must survive its malformed neighbour");
        assertEquals(Set.of(GOOD), loaded.players());
    }

    @Test
    void anEmptyListLoadsAsEmpty() {
        CompoundTag tag = new CompoundTag();
        tag.put("playerUuids", new ListTag());
        tag.put("teamUuids", new ListTag());

        AccessList loaded = AccessList.load(tag);

        assertTrue(loaded.players().isEmpty());
        assertTrue(loaded.teams().isEmpty());
    }

    @Test
    void saveAndLoadKeepTheSameUuidsAndMode() {
        AccessList list = new AccessList();
        list.setWhitelist(false);
        list.addPlayer(GOOD);
        list.addTeam(TEAM);

        AccessList reloaded = AccessList.load(list.save());

        assertEquals(Set.of(GOOD), reloaded.players());
        assertEquals(Set.of(TEAM), reloaded.teams());
        assertFalse(reloaded.isWhitelist());
    }
}
