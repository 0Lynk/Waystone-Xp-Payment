package com.olynk.waystonexppayment.auth;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

/**
 * Who may pay with (and thus drain) a tank: a whitelist or a blacklist of players and FTB Teams.
 *
 * <p>Matching is by UUID, and only by UUIDs that already exist in the game or in FTB Teams: a player's
 * game-profile UUID, an FTB team's {@code Team.getId()}. Nothing here mints an identity of its own.
 *
 * <p>Entries stored by name alone are kept for worlds saved before this model existed. They are a
 * compatibility path, not a feature: nothing new is written as a bare name, so the legacy sets only shrink.
 * An empty whitelist allows nobody, an empty blacklist allows everybody.
 */
public final class AccessList {

    private boolean whitelist = true;
    private final Set<UUID> players = new LinkedHashSet<>();
    private final Set<UUID> teams = new LinkedHashSet<>();
    private final Set<String> legacyPlayers = new LinkedHashSet<>();
    private final Set<String> legacyTeams = new LinkedHashSet<>();

    public boolean isWhitelist() {
        return whitelist;
    }

    public void setWhitelist(boolean whitelist) {
        this.whitelist = whitelist;
    }

    public Set<UUID> players() {
        return Set.copyOf(players);
    }

    public Set<UUID> teams() {
        return Set.copyOf(teams);
    }

    public Set<String> legacyPlayers() {
        return Set.copyOf(legacyPlayers);
    }

    public Set<String> legacyTeams() {
        return Set.copyOf(legacyTeams);
    }

    public boolean isEmpty() {
        return players.isEmpty() && teams.isEmpty() && legacyPlayers.isEmpty() && legacyTeams.isEmpty();
    }

    public boolean isPlayerListed(UUID playerId) {
        return players.contains(playerId);
    }

    public boolean isTeamListed(UUID teamId) {
        return teams.contains(teamId);
    }

    public boolean addPlayer(UUID playerId) {
        return players.add(playerId);
    }

    /**
     * Name-only overload kept for the command path, which may run for a player who never joined this server.
     * A UUID is stored whenever one is known; the name is then only a label until {@link #save()} drops it.
     */
    public boolean addPlayer(@Nullable String name, @Nullable UUID playerId) {
        if (playerId != null) {
            return addPlayer(playerId);
        }
        return name != null && !name.isBlank() && legacyPlayers.add(name);
    }

    public boolean removePlayer(UUID playerId) {
        return players.remove(playerId);
    }

    /**
     * Removes whatever identifies {@code key}: a UUID, or a legacy name entry. The legacy half is what makes a
     * pre-UUID entry actually disappear instead of lingering as a match that no longer has a visible row.
     */
    public boolean removePlayer(String key) {
        UUID playerId = parseUuid(key);
        if (playerId != null) {
            boolean removed = players.remove(playerId);
            boolean removedLegacy = legacyPlayers.removeIf(name -> name.equalsIgnoreCase(key));
            return removed || removedLegacy;
        }
        return legacyPlayers.removeIf(name -> name.equalsIgnoreCase(key));
    }

    /**
     * A null id means the caller had no team to name - the no-team case the resolver reports with an empty list -
     * so this is a no-op rather than an error, and it keeps an unrepresentable entry out of the saved NBT.
     */
    public boolean addTeam(@Nullable UUID teamId) {
        return teamId != null && teams.add(teamId);
    }

    public boolean removeTeam(UUID teamId) {
        return teams.remove(teamId);
    }

    public boolean removeTeam(String key) {
        UUID teamId = parseUuid(key);
        if (teamId != null) {
            boolean removed = teams.remove(teamId);
            boolean removedLegacy = legacyTeams.removeIf(name -> name.equalsIgnoreCase(key));
            return removed || removedLegacy;
        }
        return legacyTeams.removeIf(name -> name.equalsIgnoreCase(key));
    }

    /**
     * True when the given player is allowed to pay from this tank.
     *
     * <p>In whitelist mode the player must be listed (directly or through one of their teams); in blacklist
     * mode the player must not be listed.
     */
    public boolean allows(Player player, TeamResolver resolver) {
        boolean listed = players.contains(player.getUUID());
        if (!listed) {
            for (TeamResolver.TeamInfo team : resolver.teamsOf(player)) {
                if (teams.contains(team.id())) {
                    listed = true;
                    break;
                }
            }
        }
        if (!listed && hasLegacyEntries()) {
            // WHY this fallback exists: a tank saved before the UUID model holds names only. Dropping that match
            // would silently stop those tanks from paying, which reads as a broken block rather than a migration.
            listed = matchesLegacyName(player);
        }
        return whitelist == listed;
    }

    private boolean hasLegacyEntries() {
        return !legacyPlayers.isEmpty() || !legacyTeams.isEmpty();
    }

    private boolean matchesLegacyName(Player player) {
        String playerName = player.getGameProfile().getName();
        for (String name : legacyPlayers) {
            if (name.equalsIgnoreCase(playerName)) {
                return true;
            }
        }
        if (legacyTeams.isEmpty()) {
            return false;
        }
        for (TeamResolver.TeamInfo team : TeamResolvers.get().teamsOf(player)) {
            for (String name : legacyTeams) {
                if (name.equalsIgnoreCase(team.name())) {
                    return true;
                }
            }
        }
        return false;
    }

    public AccessList copy() {
        AccessList copy = new AccessList();
        copy.whitelist = whitelist;
        copy.players.addAll(players);
        copy.teams.addAll(teams);
        copy.legacyPlayers.addAll(legacyPlayers);
        copy.legacyTeams.addAll(legacyTeams);
        return copy;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("whitelist", whitelist);
        writeUuids(tag, "playerUuids", players);
        writeUuids(tag, "teamUuids", teams);
        writeStrings(tag, "legacyPlayers", legacyPlayers);
        writeStrings(tag, "legacyTeams", legacyTeams);
        return tag;
    }

    public static AccessList load(CompoundTag tag) {
        AccessList list = new AccessList();
        if (tag.contains("whitelist")) {
            list.whitelist = tag.getBoolean("whitelist");
        }
        readUuids(tag, "playerUuids", list.players);
        readUuids(tag, "teamUuids", list.teams);
        readNamesInto(tag, "legacyPlayers", list.legacyPlayers);
        readNamesInto(tag, "legacyTeams", list.legacyTeams);
        readPreviousFormat(tag, list);
        return list;
    }

    /**
     * Reads the pre-UUID format: {@code players} was a list of {uuid?, name?} compounds and {@code teams} a list
     * of {id, name?} compounds. UUIDs found here become first-class entries rather than legacy names, because a
     * UUID is exactly what the new model wants; anything left without one becomes a legacy name entry.
     */
    private static void readPreviousFormat(CompoundTag tag, AccessList list) {
        ListTag oldPlayers = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < oldPlayers.size(); i++) {
            CompoundTag playerTag = oldPlayers.getCompound(i);
            Optional<UUID> uuid = playerTag.hasUUID("uuid") ? Optional.of(playerTag.getUUID("uuid")) : Optional.empty();
            String name = playerTag.contains("name") ? playerTag.getString("name") : null;
            if (uuid.isPresent()) {
                list.players.add(uuid.get());
            } else if (name != null && !name.isBlank()) {
                list.legacyPlayers.add(name);
            }
        }

        ListTag oldTeams = tag.getList("teams", Tag.TAG_COMPOUND);
        for (int i = 0; i < oldTeams.size(); i++) {
            CompoundTag teamTag = oldTeams.getCompound(i);
            if (teamTag.hasUUID("id")) {
                list.teams.add(teamTag.getUUID("id"));
            } else if (teamTag.contains("name")) {
                String name = teamTag.getString("name");
                if (!name.isBlank()) {
                    list.legacyTeams.add(name);
                }
            }
        }
    }

    private static void writeUuids(CompoundTag tag, String key, Set<UUID> ids) {
        ListTag list = new ListTag();
        for (UUID id : ids) {
            // UUIDUtil.uuidToIntArray throws on null, and one bad entry must not cost the tank its whole access
            // list, so an id-less entry is simply not written.
            if (id != null) {
                list.add(NbtUtils.createUUID(id));
            }
        }
        tag.put(key, list);
    }

    private static void readUuids(CompoundTag tag, String key, Set<UUID> target) {
        ListTag list = tag.getList(key, Tag.TAG_INT_ARRAY);
        for (int i = 0; i < list.size(); i++) {
            try {
                target.add(NbtUtils.loadUUID(list.get(i)));
            } catch (RuntimeException e) {
                // A hand-edited or truncated int array is not worth refusing to load the whole tank for.
                target.clear();
                return;
            }
        }
    }

    private static void writeStrings(CompoundTag tag, String key, Set<String> values) {
        ListTag list = new ListTag();
        for (String value : values) {
            list.add(StringTag.valueOf(value));
        }
        tag.put(key, list);
    }

    private static void readNamesInto(CompoundTag tag, String key, Set<String> target) {
        ListTag list = tag.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            String value = list.getString(i);
            if (!value.isBlank()) {
                target.add(value);
            }
        }
    }

    @Nullable
    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
