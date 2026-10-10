package com.olynk.waystonexppayment.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

/**
 * Who may pay with (and thus drain) a tank: a whitelist or a blacklist of players and FTB Teams.
 *
 * <p>Matching is by UUID when the entry knows one, by name otherwise. An empty whitelist allows
 * nobody, an empty blacklist allows everybody.
 */
public final class AccessList {

    public record PlayerEntry(@Nullable UUID uuid, @Nullable String name) {

        public boolean matches(Player player) {
            if (uuid != null) {
                return uuid.equals(player.getUUID());
            }
            return name != null && name.equalsIgnoreCase(player.getGameProfile().getName());
        }

        boolean sameIdentity(@Nullable UUID otherUuid, @Nullable String otherName) {
            if (uuid != null && otherUuid != null) {
                return uuid.equals(otherUuid);
            }
            if (name != null && otherName != null) {
                return name.equalsIgnoreCase(otherName);
            }
            return false;
        }
    }

    public record TeamEntry(UUID id, @Nullable String name) {
    }

    private boolean whitelist = true;
    private final List<PlayerEntry> players = new ArrayList<>();
    private final List<TeamEntry> teams = new ArrayList<>();

    public boolean isWhitelist() {
        return whitelist;
    }

    public void setWhitelist(boolean whitelist) {
        this.whitelist = whitelist;
    }

    public List<PlayerEntry> players() {
        return List.copyOf(players);
    }

    public List<TeamEntry> teams() {
        return List.copyOf(teams);
    }

    public boolean isEmpty() {
        return players.isEmpty() && teams.isEmpty();
    }

    public boolean addPlayer(@Nullable String name, @Nullable UUID uuid) {
        if (name == null && uuid == null) {
            return false;
        }
        if (players.stream().anyMatch(entry -> entry.sameIdentity(uuid, name))) {
            return false;
        }
        players.add(new PlayerEntry(uuid, name));
        return true;
    }

    public boolean removePlayer(String key) {
        UUID uuid = parseUuid(key);
        return players.removeIf(entry -> (uuid != null && uuid.equals(entry.uuid()))
                || (entry.name() != null && entry.name().equalsIgnoreCase(key)));
    }

    public boolean addTeam(UUID id, @Nullable String name) {
        if (teams.stream().anyMatch(entry -> entry.id().equals(id))) {
            return false;
        }
        teams.add(new TeamEntry(id, name));
        return true;
    }

    public boolean removeTeam(UUID id) {
        return teams.removeIf(entry -> entry.id().equals(id));
    }

    public boolean removeTeam(String key) {
        UUID uuid = parseUuid(key);
        if (uuid != null) {
            return removeTeam(uuid);
        }
        return teams.removeIf(entry -> entry.name() != null && entry.name().equalsIgnoreCase(key));
    }

    /**
     * True when the given player is allowed to pay from this tank.
     *
     * <p>In whitelist mode the player must be listed (directly or through one of their teams);
     * in blacklist mode the player must not be listed.
     */
    public boolean allows(Player player, TeamResolver resolver) {
        boolean listed = players.stream().anyMatch(entry -> entry.matches(player));
        if (!listed && !teams.isEmpty()) {
            for (TeamResolver.TeamInfo team : resolver.teamsOf(player)) {
                if (teams.stream().anyMatch(entry -> entry.id().equals(team.id()))) {
                    listed = true;
                    break;
                }
            }
        }
        return whitelist == listed;
    }

    public AccessList copy() {
        AccessList copy = new AccessList();
        copy.whitelist = whitelist;
        copy.players.addAll(players);
        copy.teams.addAll(teams);
        return copy;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("whitelist", whitelist);

        ListTag playerList = new ListTag();
        for (PlayerEntry entry : players) {
            CompoundTag playerTag = new CompoundTag();
            if (entry.uuid() != null) {
                playerTag.putUUID("uuid", entry.uuid());
            }
            if (entry.name() != null) {
                playerTag.putString("name", entry.name());
            }
            playerList.add(playerTag);
        }
        tag.put("players", playerList);

        ListTag teamList = new ListTag();
        for (TeamEntry entry : teams) {
            CompoundTag teamTag = new CompoundTag();
            teamTag.putUUID("id", entry.id());
            if (entry.name() != null) {
                teamTag.putString("name", entry.name());
            }
            teamList.add(teamTag);
        }
        tag.put("teams", teamList);
        return tag;
    }

    public static AccessList load(CompoundTag tag) {
        AccessList list = new AccessList();
        if (tag.contains("whitelist")) {
            list.whitelist = tag.getBoolean("whitelist");
        }

        ListTag playerList = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < playerList.size(); i++) {
            CompoundTag playerTag = playerList.getCompound(i);
            Optional<UUID> uuid = playerTag.hasUUID("uuid") ? Optional.of(playerTag.getUUID("uuid")) : Optional.empty();
            String name = playerTag.contains("name") ? playerTag.getString("name") : null;
            if (uuid.isPresent() || name != null) {
                list.players.add(new PlayerEntry(uuid.orElse(null), name));
            }
        }

        ListTag teamList = tag.getList("teams", Tag.TAG_COMPOUND);
        for (int i = 0; i < teamList.size(); i++) {
            CompoundTag teamTag = teamList.getCompound(i);
            if (teamTag.hasUUID("id")) {
                String name = teamTag.contains("name") ? teamTag.getString("name") : null;
                list.teams.add(new TeamEntry(teamTag.getUUID("id"), name));
            }
        }
        return list;
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
