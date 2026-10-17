package com.olynk.waystonexppayment.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.olynk.waystonexppayment.net.XppTankHintPayload;
import com.olynk.waystonexppayment.net.XppTankHintQueryPayload;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * What the server said about payment tanks this client cannot see, and the queue of slots worth asking about.
 *
 * <p>The list a player sees is mostly destinations whose chunks are not loaded, so for those the client has no
 * block entity to inspect. Guessing is what this class removes: the answer is either a pool the server read (or
 * remembered), a positive "nothing there", or nothing yet - and each of those leads to a different button state.
 *
 * <p>Answers expire: a pool changes without this client being told. The queue is sent one packet per client tick,
 * so opening a long waystone list cannot turn into one packet per entry.
 */
public final class XppTankHints {

    /** How long an answer is trusted before its slot is asked about again. */
    private static final long TTL_MILLIS = 30_000L;

    private record Entry(XppTankHintPayload.Hint hint, long receivedAt) {
    }

    private record Queued(ResourceLocation dimension, BlockPos pos) {
    }

    private static final Map<String, Entry> HINTS = new ConcurrentHashMap<>();
    private static final Map<String, Queued> QUEUE = new ConcurrentHashMap<>();

    private XppTankHints() {
    }

    /** Takes one server answer into the cache. */
    public static void accept(XppTankHintPayload payload) {
        long now = System.currentTimeMillis();
        for (XppTankHintPayload.Hint hint : payload.hints()) {
            HINTS.put(key(payload.dimension(), hint.pos()), new Entry(hint, now));
        }
    }

    /**
     * @return the last answer for that slot, or {@code null} when it was never asked about or has expired
     */
    @Nullable
    public static XppTankHintPayload.Hint hint(ResourceLocation dimension, BlockPos pos) {
        String key = key(dimension, pos);
        Entry entry = HINTS.get(key);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() - entry.receivedAt() > TTL_MILLIS) {
            HINTS.remove(key);
            return null;
        }
        return entry.hint();
    }

    /** Queues a slot for the next tick, unless it already has an answer. */
    public static void request(ResourceLocation dimension, BlockPos pos) {
        if (hint(dimension, pos) != null) {
            return;
        }
        QUEUE.putIfAbsent(key(dimension, pos), new Queued(dimension, pos));
    }

    /**
     * Sends at most one query, for one dimension, on this client tick.
     *
     * <p>One dimension per packet because the query carries one: a list that mixes dimensions (a cross-dimension
     * waystone item) simply takes two ticks instead of holding a bigger packet.
     */
    public static void flush() {
        if (QUEUE.isEmpty()) {
            return;
        }
        ResourceLocation dimension = null;
        List<BlockPos> slots = new ArrayList<>(XppTankHintQueryPayload.MAX_SLOTS);
        List<String> taken = new ArrayList<>(XppTankHintQueryPayload.MAX_SLOTS);
        for (Map.Entry<String, Queued> entry : QUEUE.entrySet()) {
            Queued queued = entry.getValue();
            if (dimension == null) {
                dimension = queued.dimension();
            }
            if (!dimension.equals(queued.dimension())) {
                continue;
            }
            if (slots.size() >= XppTankHintQueryPayload.MAX_SLOTS) {
                break;
            }
            taken.add(entry.getKey());
            slots.add(queued.pos());
        }
        if (dimension == null || slots.isEmpty()) {
            return;
        }
        taken.forEach(QUEUE::remove);
        PacketDistributor.sendToServer(XppTankHintQueryPayload.of(dimension, slots));
    }

    /** Forgets everything, e.g. when leaving a world: the next world's answers are not these answers. */
    public static void clear() {
        HINTS.clear();
        QUEUE.clear();
    }

    private static String key(ResourceLocation dimension, BlockPos pos) {
        return dimension + "@" + pos.asLong();
    }
}
