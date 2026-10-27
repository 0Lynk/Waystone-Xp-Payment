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
 * <p>Answers are kept and re-asked rather than dropped: forgetting an old one turns "this tank is empty" back into
 * "no information", and the preview reads no information as "can pay" - which left the entry of a drained tank lit
 * for as long as the player kept the list open. An answer older than {@link #TRUSTED_MILLIS} still decides the
 * button and is re-asked at most once per {@link #REASK_INTERVAL_MILLIS}; the queue is sent one packet per client
 * tick, so a long list still cannot turn into one packet per entry.
 */
public final class XppTankHints {

    /** How long an answer is trusted without asking the server about that slot again. */
    private static final long TRUSTED_MILLIS = 30_000L;

    /** How often one slot may be re-asked while its answer is older than {@link #TRUSTED_MILLIS}. */
    private static final long REASK_INTERVAL_MILLIS = 1_000L;

    /**
     * How old an answer may be when a list is built on it, before that list asks again.
     *
     * <p>Far shorter than {@link #TRUSTED_MILLIS} because the answer a player needs is the one from after their last
     * trip: paying out of a far-away tank drains it, and the list opened at the destination has to hear about that
     * within a tick or two rather than half a minute later.
     */
    private static final long LIST_OPEN_MAX_AGE_MILLIS = 5_000L;

    private record Entry(XppTankHintPayload.Hint hint, long receivedAt) {
    }

    private record Queued(ResourceLocation dimension, BlockPos pos) {
    }

    private static final Map<String, Entry> HINTS = new ConcurrentHashMap<>();
    private static final Map<String, Queued> QUEUE = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_ASKED = new ConcurrentHashMap<>();

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
     * @return the last answer for that slot, or {@code null} when it was never asked about. An old answer is
     * returned on purpose - see the class comment.
     */
    @Nullable
    public static XppTankHintPayload.Hint hint(ResourceLocation dimension, BlockPos pos) {
        Entry entry = HINTS.get(key(dimension, pos));
        return entry == null ? null : entry.hint();
    }

    /**
     * Queues a slot for the next tick unless its answer is recent enough to build a list on.
     *
     * <p>Asked on every list build, so reopening the screen after a trip re-reads the tanks that trip could have
     * drained. A slot that was answered seconds ago is skipped: the button keeps refreshing it through
     * {@link #refreshIfStale} while the list is open.
     */
    public static void request(ResourceLocation dimension, BlockPos pos) {
        String key = key(dimension, pos);
        Entry entry = HINTS.get(key);
        long now = System.currentTimeMillis();
        if (entry != null && !dueForListOpen(entry.receivedAt(), now)) {
            return;
        }
        QUEUE.putIfAbsent(key, new Queued(dimension, pos));
    }

    /** Whether an answer from {@code receivedAt} is too old to build a list on. */
    static boolean dueForListOpen(long receivedAt, long now) {
        return now - receivedAt > LIST_OPEN_MAX_AGE_MILLIS;
    }

    /**
     * Queues a slot again when its answer has gone stale, so a pool that changed elsewhere reaches the button.
     *
     * <p>Called from the render path, once per entry per frame: it only reads two maps and drops the re-ask when the
     * previous one is younger than {@link #REASK_INTERVAL_MILLIS}, so the queue holds one entry per slot and
     * {@link #flush()} still sends a single packet per tick.
     */
    public static void refreshIfStale(ResourceLocation dimension, BlockPos pos) {
        String key = key(dimension, pos);
        Entry entry = HINTS.get(key);
        if (entry == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!dueForReask(entry.receivedAt(), LAST_ASKED.get(key), now)) {
            return;
        }
        LAST_ASKED.put(key, now);
        QUEUE.putIfAbsent(key, new Queued(dimension, pos));
    }

    /**
     * Whether a slot answered at {@code receivedAt} should be asked about again at {@code now}.
     *
     * <p>Split out so both thresholds are unit tested without a client world: a fresh answer is trusted, and a stale
     * one is re-asked no more often than the interval.
     *
     * @param lastAskedAt when this slot was last queued for a re-ask, or {@code null} when it never was
     */
    static boolean dueForReask(long receivedAt, @Nullable Long lastAskedAt, long now) {
        if (now - receivedAt <= TRUSTED_MILLIS) {
            return false;
        }
        return lastAskedAt == null || now - lastAskedAt >= REASK_INTERVAL_MILLIS;
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
        LAST_ASKED.clear();
    }

    private static String key(ResourceLocation dimension, BlockPos pos) {
        return dimension + "@" + pos.asLong();
    }
}
