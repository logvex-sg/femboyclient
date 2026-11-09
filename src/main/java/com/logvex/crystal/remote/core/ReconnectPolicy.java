package com.logvex.crystal.remote.core;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Exponential backoff with jitter for agent reconnection.
 *
 * <p>An agent whose controller is down must keep trying, but must not hammer: the delay grows
 * from {@link #baseMillis} up to {@link #maxMillis} and is randomised by {@link #jitterFactor} so
 * a fleet that all lost the same controller does not reconnect in lockstep. The jitter also means
 * a restarting controller is not hit by twenty agents in the same millisecond.
 *
 * <p>Minecraft-independent and unit testable (the jitter is the only randomness; the growth is
 * deterministic).
 */
public final class ReconnectPolicy {

    private final long baseMillis;
    private final long maxMillis;
    private final double multiplier;
    private final double jitterFactor;

    private int attempt;

    public ReconnectPolicy(long baseMillis, long maxMillis, double multiplier, double jitterFactor) {
        this.baseMillis = Math.max(50L, baseMillis);
        this.maxMillis = Math.max(this.baseMillis, maxMillis);
        this.multiplier = Math.max(1.0, multiplier);
        this.jitterFactor = Math.min(1.0, Math.max(0.0, jitterFactor));
    }

    /** The default policy: 500ms growing to 30s, with 20% jitter. */
    public static ReconnectPolicy standard() {
        return new ReconnectPolicy(500L, 30_000L, 2.0, 0.2);
    }

    /** A fast policy for tests. */
    public static ReconnectPolicy fast() {
        return new ReconnectPolicy(50L, 500L, 2.0, 0.1);
    }

    /** How many times {@link #nextDelayMillis()} has been called since the last {@link #reset()}. */
    public int attempt() {
        return attempt;
    }

    /** The delay before the next attempt, and advances the attempt counter. */
    public long nextDelayMillis() {
        long base = Math.min(maxMillis, (long) (baseMillis * Math.pow(multiplier, attempt)));
        attempt++;
        if (jitterFactor <= 0d) {
            return base;
        }
        double jitter = base * jitterFactor;
        long low = Math.max(baseMillis, (long) (base - jitter));
        long high = (long) (base + jitter);
        if (high <= low) {
            return low;
        }
        return ThreadLocalRandom.current().nextLong(low, high + 1);
    }

    /** Called after a successful connection, so the next failure starts from the base delay. */
    public void reset() {
        attempt = 0;
    }

    /** The theoretical (unjittered) delay for attempt {@code n}, for tests and docs. */
    public long delayForAttempt(int n) {
        return Math.min(maxMillis, (long) (baseMillis * Math.pow(multiplier, Math.max(0, n))));
    }
}
