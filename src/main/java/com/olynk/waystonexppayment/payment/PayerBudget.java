package com.olynk.waystonexppayment.payment;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spend limit for "load a destination chunk only to read its payment tank".
 *
 * <p>Loading a chunk is the one expensive thing the remote-destination path can do, and it runs on the server
 * thread while a player's click is being handled. Without a cap, a player who clicks a long list of waystones in
 * a row turns that into a long list of synchronous loads; without a short memo, a slot that could not be loaded
 * is retried on every click.
 *
 * <p>Two different lifetimes on purpose: the count belongs to one teleport attempt (a fresh budget per event),
 * while the memo has to outlive it, because the retry that matters is the player clicking again a second later.
 * Deliberately a plain object with an injected clock, so the accounting is testable without a running server.
 */
public final class PayerBudget {

    private static final Map<String, Long> RECENT_ATTEMPTS = new ConcurrentHashMap<>();

    private final int chunkLoads;
    private int used;

    /** @param chunkLoads how many chunk loads one teleport attempt may spend (clamped to {@code >= 0}) */
    public PayerBudget(int chunkLoads) {
        this.chunkLoads = Math.max(0, chunkLoads);
    }

    /**
     * Asks permission to load the chunk behind {@code slot} and consumes budget when it is granted.
     *
     * <p>A granted load normally leaves the chunk resident, so the next click on the same entry takes the
     * already-loaded path and never comes back here; the memo therefore only matters for the failure case.
     *
     * @param slot a stable identifier for the destination slot (dimension and position)
     * @param nowMillis current time, injected so tests do not have to sleep
     * @param memoMillis how long a failed attempt suppresses a retry for the same slot
     * @return true when the caller may load, false when the budget is spent or the same slot was tried inside
     *         the memo window
     */
    public boolean requestLoad(String slot, long nowMillis, long memoMillis) {
        Long triedAt = RECENT_ATTEMPTS.get(slot);
        if (triedAt != null && nowMillis < triedAt) {
            return false;
        }
        if (used >= chunkLoads) {
            return false;
        }
        used++;
        rememberUntil(slot, nowMillis + Math.max(0L, memoMillis));
        return true;
    }

    /** How many loads this budget has granted so far. */
    public int used() {
        return used;
    }

    /** How many loads this budget may grant in total. */
    public int limit() {
        return chunkLoads;
    }

    /**
     * Keeps the memo from growing without bound.
     *
     * <p>Entries are tiny and a server has a finite number of waystone slots it can be asked about, but a
     * long-running world is exactly where "it cannot grow much" stops being true.
     */
    private static void rememberUntil(String slot, long until) {
        if (RECENT_ATTEMPTS.size() > 1024) {
            long now = System.currentTimeMillis();
            RECENT_ATTEMPTS.entrySet().removeIf(entry -> entry.getValue() < now);
        }
        RECENT_ATTEMPTS.put(slot, until);
    }

    /** Drops the cross-attempt memo; only tests need this. */
    static void clearMemo() {
        RECENT_ATTEMPTS.clear();
    }
}
