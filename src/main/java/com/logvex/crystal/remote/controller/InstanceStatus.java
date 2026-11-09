package com.logvex.crystal.remote.controller;

/**
 * The connection state of one managed instance, as the dashboard shows it.
 *
 * <p>The states are ordered so a UI can colour them consistently and a caller can ask "is this
 * instance usable right now" with one check. {@link #AWAITING_PAIRING} is distinct from
 * {@link #UNAUTHORIZED} on purpose: the first means the agent is connected but has not been
 * paired yet (the user can fix it from the dashboard), the second means it failed to prove it
 * holds the secret (a real security event).
 */
public enum InstanceStatus {

    /** No live link; the instance is known only from its last pairing/snapshot. */
    OFFLINE("Offline"),

    /** A link is being established. */
    CONNECTING("Connecting"),

    /** Connected and identified, but the controller has no secret for it yet. */
    AWAITING_PAIRING("Awaiting pairing"),

    /** The peer's protocol version is not one this controller speaks. */
    INCOMPATIBLE("Incompatible"),

    /** The peer failed to authenticate, or its token was revoked. */
    UNAUTHORIZED("Unauthorized"),

    /** Authenticated and exchanging status; commands may be sent. */
    ONLINE("Online"),

    /** The link dropped unexpectedly and may be retried by the agent. */
    DISCONNECTED("Disconnected");

    private final String label;

    InstanceStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** True when commands can be sent to this instance right now. */
    public boolean controllable() {
        return this == ONLINE;
    }

    /** True when the instance is present in some form, connected or not. */
    public boolean known() {
        return this != OFFLINE;
    }
}
