package com.logvex.crystal.remote.security;

/**
 * A token-bucket rate limiter.
 *
 * <p>Even an authenticated peer can flood: a buggy controller that retries in a tight loop, or a
 * compromised agent, should not be able to pin a CPU or fill a queue. The bucket allows a burst
 * up to {@code capacity} and then a steady {@code refillPerSecond}, which is the right shape for
 * control traffic (occasional bursts of a few commands, long idle periods).
 *
 * <p>Not thread safe by itself; each connection owns one limiter and calls it from its reader
 * thread. Minecraft-independent and unit testable.
 */
public final class RateLimiter {

    private final double capacity;
    private final double refillPerSecond;
    private double tokens;
    private long lastRefillNanos;

    public RateLimiter(double capacity, double refillPerSecond) {
        this.capacity = Math.max(1d, capacity);
        this.refillPerSecond = Math.max(0.01d, refillPerSecond);
        this.tokens = this.capacity;
        this.lastRefillNanos = System.nanoTime();
    }

    /** A limiter suited to control traffic: bursts of 40, then 20 messages per second. */
    public static RateLimiter forControlTraffic() {
        return new RateLimiter(40, 20);
    }

    /**
     * Tries to take one token.
     *
     * @return true when allowed (and a token was taken), false when the bucket is empty
     */
    public synchronized boolean tryAcquire() {
        return tryAcquire(1d);
    }

    /** Tries to take {@code cost} tokens; false when there are not enough. */
    public synchronized boolean tryAcquire(double cost) {
        refill();
        if (tokens >= cost) {
            tokens -= cost;
            return true;
        }
        return false;
    }

    /** Tokens currently available, for diagnostics. */
    public synchronized double available() {
        refill();
        return tokens;
    }

    private void refill() {
        long now = System.nanoTime();
        double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000d;
        if (elapsedSeconds > 0) {
            tokens = Math.min(capacity, tokens + elapsedSeconds * refillPerSecond);
            lastRefillNanos = now;
        }
    }
}
