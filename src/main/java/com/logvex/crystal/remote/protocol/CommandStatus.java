package com.logvex.crystal.remote.protocol;

/**
 * The state of a command on one instance, from the controller's point of view.
 *
 * <p>The distinction that matters most is {@link #UNSUPPORTED} versus {@link #FAILED}: an
 * instance that cannot perform an action must be reported as unsupported, never as success and
 * never as a generic failure. {@link #PENDING} is the state before any acknowledgement arrives —
 * the controller must not treat a command as done while it is still pending.
 */
public enum CommandStatus {

    /** Accepted by the agent and completed successfully. */
    OK("ok"),

    /** The instance does not advertise the capability the action needs. */
    UNSUPPORTED("unsupported"),

    /** The action is outside the instance's allowlist or permissions. */
    REJECTED("rejected"),

    /** The agent accepted the action but it failed while running. */
    FAILED("failed"),

    /** No acknowledgement arrived before the deadline. */
    TIMEOUT("timeout"),

    /** Cancelled by the user or the controller before completion. */
    CANCELLED("cancelled"),

    /** Sent, awaiting acknowledgement. */
    PENDING("pending");

    private final String wire;

    CommandStatus(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    /** True for a terminal state (no further change is expected). */
    public boolean terminal() {
        return this != PENDING;
    }

    /** True when the command achieved what it was asked to do. */
    public boolean succeeded() {
        return this == OK;
    }

    public static CommandStatus parse(String wire) {
        if (wire == null) {
            return FAILED;
        }
        String needle = wire.trim().toLowerCase(java.util.Locale.ROOT);
        for (CommandStatus status : values()) {
            if (status.wire.equals(needle)) {
                return status;
            }
        }
        return FAILED;
    }
}
