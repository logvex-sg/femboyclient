package com.logvex.crystal.scan.world;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A cooperative cancellation token.
 *
 * <p>Background work (classification, persistence) checks {@link #isCancelled()} at safe points
 * and returns early rather than being interrupted mid-operation, so a cancellation never leaves a
 * half-written record. A token is single-use: once cancelled it stays cancelled, and the owning
 * run creates a fresh one.
 */
public final class CancellationToken {

    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final String reason;

    public CancellationToken() {
        this("");
    }

    public CancellationToken(String reason) {
        this.reason = reason == null ? "" : reason;
    }

    /** Requests cancellation. Idempotent. */
    public void cancel(String why) {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public String reason() {
        return reason;
    }
}
