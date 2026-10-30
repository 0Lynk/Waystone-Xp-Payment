package com.olynk.waystonexppayment.net;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.olynk.waystonexppayment.WaystoneXpPaymentMod;
import com.olynk.waystonexppayment.auth.AccessList;
import com.olynk.waystonexppayment.auth.PlayerNames;
import com.olynk.waystonexppayment.auth.TeamResolver;
import com.olynk.waystonexppayment.auth.TeamResolvers;
import com.olynk.waystonexppayment.block.entity.XpPaymentTankBlockEntity;
import com.olynk.waystonexppayment.config.XppSettings;

import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server -> client snapshot of everything the tank screen shows.
 *
 * <p>{@code candidatesPlayers} / {@code candidatesTeams} are the picker source: the server decides what may be
 * added at all (online players, existing teams), and the screen only filters that list locally. {@code canEdit}
 * is the owner check the screen renders as read-only mode; the server re-checks it on every request anyway.
 */
public record XppStatePayload(BlockPos pos, FluidStack fluid, int capacity, int poolXp, int xpPerBucketMilli,
                              boolean whitelist, boolean canEdit, String ownerName, List<Entry> candidatesPlayers,
                              List<Entry> candidatesTeams, List<Entry> entries, List<Entry> ownTeams)
        implements CustomPacketPayload {

    /**
     * One row of the access list, in the order the screen shows it.
     *
     * <p>Players and teams share this record on purpose: the list is one list to the user, and {@code team} is
     * only a rendering flag. {@code id} is {@code null} for an entry that was stored by name before the UUID
     * model existed - such a row still has to be visible, otherwise it could never be removed from the GUI.
     * {@code mine} marks the sending player's own identity or one of their teams.
     */
    public record Entry(@Nullable UUID id, String label, boolean team, boolean mine) {
    }

    /**
     * Wire cap on how many rows one snapshot may carry.
     *
     * <p>The count is a VarInt off the network, and {@code new ArrayList<>(size)} trusts it: a client that sends
     * a huge number would have the server allocate that many slots before the first entry is even read. No tank
     * screen shows more than a handful of rows, so anything past this is malformed or hostile.
     */
    public static final int MAX_ENTRIES = XppWireLimits.MAX_LIST;

    public static final CustomPacketPayload.Type<XppStatePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    WaystoneXpPaymentMod.MOD_ID, "tank_state"));

    // OPTIONAL_STREAM_CODEC, not STREAM_CODEC: the plain one is backed by FluidStack's non-empty codec and
    // throws "Empty FluidStack not allowed" for an empty tank, which fails the packet and disconnects the
    // player (observed on a live client: empty tank + right click -> EncoderException -> lost connection).
    public static final StreamCodec<RegistryFriendlyByteBuf, XppStatePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.pos());
                FluidStack.OPTIONAL_STREAM_CODEC.encode(buffer, payload.fluid());
                buffer.writeVarInt(payload.capacity());
                buffer.writeVarInt(payload.poolXp());
                buffer.writeVarInt(payload.xpPerBucketMilli());
                buffer.writeBoolean(payload.whitelist());
                buffer.writeBoolean(payload.canEdit());
                buffer.writeUtf(XppWireLimits.text(payload.ownerName()), XppWireLimits.MAX_TEXT_BYTES);
                writeEntries(buffer, payload.candidatesPlayers());
                writeEntries(buffer, payload.candidatesTeams());
                writeEntries(buffer, payload.entries());
                writeEntries(buffer, payload.ownTeams());
            },
            buffer -> new XppStatePayload(
                    buffer.readBlockPos(),
                    FluidStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readUtf(64),
                    readEntries(buffer),
                    readEntries(buffer),
                    readEntries(buffer),
                    readEntries(buffer)));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Builds and sends the current state of the tank to one player. */
    public static void sendTo(ServerPlayer player, BlockPos pos, XpPaymentTankBlockEntity tank,
                              List<TeamResolver.TeamInfo> ownTeams) {
        AccessList access = tank.access();
        Set<UUID> ownTeamIds = new HashSet<>();
        for (TeamResolver.TeamInfo team : ownTeams) {
            ownTeamIds.add(team.id());
        }
        UUID viewer = player.getUUID();

        List<Entry> entries = new ArrayList<>();
        for (UUID playerId : access.players()) {
            entries.add(new Entry(playerId, labelForPlayer(player, playerId), false, playerId.equals(viewer)));
        }
        for (String name : access.legacyPlayers()) {
            // A legacy row is never "mine": the stored name is not proof that this is the sending player, and a
            // wrong check mark would invite a click that silently does nothing.
            entries.add(new Entry(null, name, false, false));
        }
        for (UUID teamId : access.teams()) {
            entries.add(new Entry(teamId, labelForTeam(ownTeams, teamId), true, ownTeamIds.contains(teamId)));
        }
        for (String name : access.legacyTeams()) {
            entries.add(new Entry(null, name, true, false));
        }

        List<Entry> own = new ArrayList<>();
        for (TeamResolver.TeamInfo team : ownTeams) {
            // Sent with the id so the screen can add a team without the server having to match a name again.
            own.add(new Entry(team.id(), team.name(), true, true));
        }

        PacketDistributor.sendToPlayer(player, new XppStatePayload(pos, tank.getTank().getFluid(),
                tank.getTank().getCapacity(), tank.poolXp(), tank.xpPerBucketMilli(), access.isWhitelist(),
                tank.canEditOrOp(player), tank.ownerName(), candidatePlayers(player), candidateTeams(),
                entries, own));
    }

    /**
     * Everyone currently online, as picker rows.
     *
     * <p>The server decides who may be added, the client only filters the list, so this must not be names read
     * back from the wire. Sorted by name because the wire order is the login order, which reads as random.
     */
    private static List<Entry> candidatePlayers(ServerPlayer viewer) {
        List<Entry> candidates = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (ServerPlayer online : viewer.server.getPlayerList().getPlayers()) {
            UUID id = online.getUUID();
            if (seen.add(id)) {
                candidates.add(new Entry(id, online.getGameProfile().getName(), false, false));
            }
        }
        candidates.sort(Comparator.comparing(entry -> entry.label(), String.CASE_INSENSITIVE_ORDER));
        return candidates;
    }

    /** Every team the team mod knows, as picker rows. Empty without FTB Teams. */
    private static List<Entry> candidateTeams() {
        List<Entry> candidates = new ArrayList<>();
        for (TeamResolver.TeamInfo team : TeamResolvers.get().allTeams()) {
            candidates.add(new Entry(team.id(), team.name(), true, false));
        }
        return candidates;
    }

    /**
     * Best label for a stored player UUID: the sending player's own name, then whoever is online under it, then
     * the name the profile cache still remembers, then a short id.
     *
     * <p>WHY the cache is in the chain: without it every member who logged off turned into a raw 36-character
     * UUID on their row, which is the exact case {@link PlayerNames#remembered} exists for. The precedence itself
     * is {@link PlayerNames#label}, shared with the team member list.
     */
    private static String labelForPlayer(ServerPlayer viewer, UUID playerId) {
        if (playerId.equals(viewer.getUUID())) {
            return viewer.getGameProfile().getName();
        }
        ServerPlayer online = viewer.server.getPlayerList().getPlayer(playerId);
        return PlayerNames.label(playerId, online == null ? null : online.getGameProfile().getName(),
                PlayerNames.remembered(viewer.server, playerId));
    }

    /**
     * Best label for a stored team UUID. Only the sending player's own teams can be named from the snapshot, so
     * any other team falls back to its UUID string.
     */
    private static String labelForTeam(List<TeamResolver.TeamInfo> ownTeams, UUID teamId) {
        for (TeamResolver.TeamInfo team : ownTeams) {
            if (team.id().equals(teamId)) {
                return team.name();
            }
        }
        return teamId.toString();
    }

    /** Tank capacity exposed for the screen's "x / y mB" line; falls back to the configured value. */
    public int capacityOrDefault() {
        return capacity > 0 ? capacity : XppSettings.current().capacityMb();
    }

    private static void writeEntries(FriendlyByteBuf buffer, List<Entry> entries) {
        // Capped on the way out, not only on the way in: the reader refuses anything past MAX_ENTRIES, and a
        // refused snapshot closes the screen whose access list is the only way to shorten that list - a server
        // that keeps sending 257 rows is a tank nobody can ever edit again. The viewer's own rows survive the
        // cut first, so a full list cannot cost them the entry they are looking for.
        List<Entry> ordered = entries.size() <= XppWireLimits.MAX_LIST ? entries : mineFirst(entries);
        List<Entry> capped = XppWireLimits.cap(ordered);
        buffer.writeVarInt(capped.size());
        for (Entry entry : capped) {
            buffer.writeBoolean(entry.id() != null);
            if (entry.id() != null) {
                UUIDUtil.STREAM_CODEC.encode(buffer, entry.id());
            }
            // 64 is a UTF-8 *byte* budget: a team name of 22 Chinese characters is already over it, and the
            // encode would throw on the sending side.
            buffer.writeUtf(XppWireLimits.text(entry.label()), XppWireLimits.MAX_TEXT_BYTES);
            buffer.writeBoolean(entry.team());
            buffer.writeBoolean(entry.mine());
        }
    }

    /**
     * Stable partition, own rows first.
     *
     * <p>Only used when the list has to be cut; the normal order stays the order the screen has always shown,
     * because reordering rows under a moving mouse is its own bug.
     */
    private static List<Entry> mineFirst(List<Entry> entries) {
        List<Entry> ordered = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            if (entry.mine()) {
                ordered.add(entry);
            }
        }
        for (Entry entry : entries) {
            if (!entry.mine()) {
                ordered.add(entry);
            }
        }
        return ordered;
    }

    private static List<Entry> readEntries(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        requireEntryCount(size);
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            UUID id = buffer.readBoolean() ? UUIDUtil.STREAM_CODEC.decode(buffer) : null;
            entries.add(new Entry(id, buffer.readUtf(XppWireLimits.MAX_TEXT_BYTES), buffer.readBoolean(),
                    buffer.readBoolean()));
        }
        return entries;
    }

    /**
     * Refuses a row count the wire should never carry.
     *
     * <p>Package-private so the bounds can be unit-tested without a live registry: the decode path itself needs a
     * {@code RegistryFriendlyByteBuf}, the rule does not.
     */
    static void requireEntryCount(int size) {
        if (size < 0 || size > MAX_ENTRIES) {
            // Reading on regardless would leave the buffer mid-entry and corrupt every later payload on this
            // connection, so the packet is refused instead.
            throw new DecoderException("Entry count out of range: " + size);
        }
    }
}
