package com.logvex.crystal.remote.core;

import com.logvex.crystal.remote.protocol.RemoteMessage;

/**
 * What a {@link RemoteLink} tells its owner: an inbound message, and why it stopped.
 *
 * <p>Callbacks arrive on the link's own reader/writer threads, never the game thread, so an
 * implementation must hand anything that touches the client off to the game thread (the agent
 * bridge does this by queueing). That rule is the reason the link can be used from inside a
 * Minecraft client at all.
 *
 * <p>Minecraft-independent.
 */
public interface LinkListener {

    /** A decoded, size- and rate-checked message from the peer. */
    void onMessage(RemoteMessage message);

    /**
     * The link closed.
     *
     * @param reason a short human reason ({@code "peer closed"}, {@code "heartbeat timeout"}, ...)
     * @param fatal  true when reconnection should be attempted, false after a clean BYE
     */
    void onClosed(String reason, boolean fatal);

    /** A non-fatal problem (a bad frame, a rate-limited message). Never closes the link by itself. */
    default void onWarning(String message) {
    }
}
