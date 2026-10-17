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
     * <p>Granting does not remember anything: see {@link #rememberMiss} for why only a miss may.
     *
     * @param slot a stable identifier for the destination slot (dimension and position)
     * @param nowMillis current time, injected so tests do not have to sleep
     * @return true when the caller may load, false when the budget is spent or the same slot missed inside the
     *         memo window
     */
    public boolean requestLoad(String slot, long nowMillis) {
        Long missedAt = RECENT_ATTEMPTS.get(slot);
        if (missedAt != null && nowMillis < missedAt) {
            return false;
        }
        if (used >= chunkLoads) {
            return false;
        }
        used++;
        return true;
    }

    /**
     * Remembers that loading {@code slot} produced no paying tank, so the next click does not pay for the same
     * fruitless load again.
     *
     * <p>Only a miss is remembered. A load that did find a tank leaves the chunk resident - but only until the
     * player leaves it, and a destination is exactly where the player does not stay. Remembering the success
     * would then refuse the next trip's only load, and the player would be told they cannot afford a teleport
     * that the very same tank was paying for seconds earlier.
     *
     * @param slot stable identifier for the destination slot
     * @param nowMillis when the miss happened
     * @param memoMillis how long a retry for the same slot stays suppressed
     */
    public void rememberMiss(String slot, long nowMillis, long memoMillis) {
        rememberUntil(slot, nowMillis + Math.max(0L, memoMillis));
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
