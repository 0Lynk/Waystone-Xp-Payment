package com.olynk.waystonexppayment.client;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.olynk.waystonexppayment.net.XppAccessPayload.Action;

/**
 * The one access-list edit that is currently in flight, and how a snapshot proves what happened to it.
 *
 * <p>The server answers every request with a fresh snapshot, including a refusal, so "did my edit take effect"
 * is decided by looking for the result in that snapshot rather than by trusting a reply code.
 *
 * <p>Kept free of client and Minecraft types on purpose: the judgement is the part worth testing, and it has
 * three outcomes, not two (see {@link Verdict}).
 */
record PendingRequest(Action action, List<UUID> targets, String label, boolean wantWhitelist, long sentAt) {

    /** The part of a snapshot row this judgement needs, so the rule stays independent of the wire format. */
    record Entry(@Nullable UUID id, String label, boolean team) {
    }

    enum Verdict {
        /** The snapshot shows the edit went through. */
        APPLIED,
        /** The snapshot answers this request and does not show the edit. */
        REFUSED,
        /** The snapshot is not the whole answer yet, so the request stays in flight. */
        INCOMPLETE
    }

    Verdict verdict(List<Entry> entries, boolean whitelist) {
        boolean satisfied = switch (action) {
            case ADD_PLAYER -> allPresent(entries, false);
            case ADD_TEAM -> allPresent(entries, true);
            // A removal is proven by absence: the entry has to be gone, by id or - for a legacy row that has
            // no id - by the label the request carried along for exactly that case.
            case REMOVE_PLAYER -> nonePresent(entries, false);
            case REMOVE_TEAM -> nonePresent(entries, true);
            case SET_MODE -> whitelist == wantWhitelist;
            // REFRESH changes nothing to check, and members are answered by their own payload.
            case REFRESH, REQUEST_MEMBERS -> false;
        };
        if (satisfied) {
            return Verdict.APPLIED;
        }
        // A request with one target gets exactly one snapshot back, so a miss in it is the server's refusal.
        // A bulk request sent one packet per target: the first snapshot back shows only the first addition,
        // so a miss there is not yet an answer and the request waits for the timeout instead.
        return targets.size() <= 1 ? Verdict.REFUSED : Verdict.INCOMPLETE;
    }

    /** True when the row described here is the subject of this request, so the panel can mark it as in flight. */
    boolean concerns(@Nullable UUID id, String rowLabel, boolean team) {
        for (UUID target : targets) {
            if (target != null && target.equals(id)) {
                return true;
            }
        }
        // A legacy row has no id to match on, so the label is the only identity the request could carry.
        return id == null && !label.isEmpty() && label.equals(rowLabel);
    }

    /** An id-less target is a legacy entry, which the snapshot can only show by name. */
    private boolean matches(Entry entry, UUID target, boolean team, boolean byLabel) {
        if (entry.team() != team) {
            return false;
        }
        if (target != null) {
            return target.equals(entry.id());
        }
        return byLabel && !label.isEmpty() && label.equalsIgnoreCase(entry.label());
    }

    private boolean allPresent(List<Entry> entries, boolean team) {
        for (UUID target : targets) {
            if (!anyMatch(entries, target, team)) {
                return false;
            }
        }
        return true;
    }

    private boolean nonePresent(List<Entry> entries, boolean team) {
        for (UUID target : targets) {
            if (anyMatch(entries, target, team)) {
                return false;
            }
        }
        return true;
    }

    private boolean anyMatch(List<Entry> entries, @Nullable UUID target, boolean team) {
        for (Entry entry : entries) {
            if (matches(entry, target, team, true)) {
                return true;
            }
        }
        return false;
    }
}
