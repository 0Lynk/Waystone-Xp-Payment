package com.olynk.waystonexppayment.net;

import javax.annotation.Nullable;

/**
 * Reply status of {@link XppMembersPayload}, and the panel state it maps to.
 *
 * <p>Separate from the payload record on purpose: the mapping is a pure function, so it can be tested without
 * a client. The member panel has to tell "this team has no members" apart from "this server has no team mod",
 * and the only thing that differs between those two cases is this one byte.
 */
public final class XppMembersStatus {

    public static final byte OK = 0;
    public static final byte NO_TEAM_MOD = 1;
    public static final byte UNKNOWN_TEAM = 2;
    public static final byte NOT_OWNER = 3;

    /** What the member panel shows. LOADING and TIMEOUT are client-local: no reply ever carries them. */
    public enum Phase {
        LOADING, OK, EMPTY, NO_TEAM_MOD, UNKNOWN_TEAM, DENIED, TIMEOUT
    }

    private XppMembersStatus() {
    }

    /** Phase for a reply that arrived; {@code memberCount} only matters for {@link #OK}. */
    public static Phase phaseFor(byte status, int memberCount) {
        return switch (status) {
            case OK -> memberCount > 0 ? Phase.OK : Phase.EMPTY;
            case UNKNOWN_TEAM -> Phase.UNKNOWN_TEAM;
            case NOT_OWNER -> Phase.DENIED;
            // A byte outside the four known values can only come from a peer that does not speak this version
            // of the protocol, and "the server has no team data for you" is the honest reading of that.
            default -> Phase.NO_TEAM_MOD;
        };
    }

    /** Key of the sentence drawn in the row area, or {@code null} when the phase draws rows instead. */
    @Nullable
    public static String messageKey(Phase phase) {
        return switch (phase) {
            case LOADING -> "access.members.loading";
            case EMPTY -> "access.members.empty";
            case NO_TEAM_MOD -> "access.members.no_team_mod";
            case UNKNOWN_TEAM -> "access.members.unknown_team";
            case DENIED -> "access.members.denied";
            case TIMEOUT -> "access.members.timeout";
            case OK -> null;
        };
    }

    /** Key of the second line a phase adds under its sentence, or {@code null} when it adds none. */
    @Nullable
    public static String secondLineKey(Phase phase) {
        return phase == Phase.TIMEOUT ? "access.members.retry" : null;
    }
}
