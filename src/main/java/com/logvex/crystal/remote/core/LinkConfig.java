package com.logvex.crystal.remote.core;

/**
 * How a link behaves: queue sizes, timeouts and message limits.
 *
 * <p>Every bound here exists to protect the game from a misbehaving peer. The outbound queue is
 * bounded so a dead connection cannot grow memory without limit; the heartbeat interval and
 * timeout let a half-open socket be detected (the heartbeat closes the socket, which unblocks
 * the reader, so no socket read timeout is used); the per-link message rate caps how much work
 * an authenticated-but-buggy peer can create. Defaults suit a desktop client; a test can shrink them.
 *
 * <p>Minecraft-independent.
 */
public final class LinkConfig {

    /** Outbound messages buffered before the oldest are dropped (never unbounded). */
    public int outboundQueueSize = 256;

    /** How often each side sends a PING. */
    public long heartbeatIntervalMillis = 5_000L;

    /** How long without any message before a link is considered dead. */
    public long heartbeatTimeoutMillis = 20_000L;

    /** Incoming messages per second allowed from a peer, after a burst. */
    public double inboundMessagesPerSecond = 50d;

    /** Incoming burst allowance. */
    public double inboundBurst = 100d;

    /** Consecutive bad messages tolerated before the link is closed. */
    public int maxBadMessages = 5;

    public static LinkConfig defaults() {
        return new LinkConfig();
    }

    /** A config tuned for tests: fast heartbeats, small queues. */
    public static LinkConfig test() {
        LinkConfig config = new LinkConfig();
        config.outboundQueueSize = 32;
        config.heartbeatIntervalMillis = 250L;
        config.heartbeatTimeoutMillis = 2_000L;
        return config;
    }
}
