package com.logvex.crystal.remote.security;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Rejects a message whose nonce has been seen before, or whose timestamp is outside a small
 * window around now.
 *
 * <p>HMAC proves a message was authentic when it was sent; it does not prove it is <em>new</em>.
 * An attacker who records a valid "disable KillAura" command and replays it later would otherwise
 * succeed, because the tag still verifies. Two checks close that hole: the timestamp must be
 * within {@link #WINDOW_MILLIS} of the receiver's clock, and the 128-bit nonce must not have been
 * seen within that same window. Once a nonce falls out of the window it is forgotten, so the
 * memory cost is bounded by the message rate.
 *
 * <p>Not thread safe by itself; each connection owns one guard and drives it from its reader
 * thread. Minecraft-independent and unit testable.
 */
public final class ReplayGuard {

    /** How far a message's timestamp may be from the receiver's clock, in milliseconds. */
    public static final long WINDOW_MILLIS = 120_000L;

    private final Deque<Entry> seen = new ArrayDeque<>();
    private final Set<String> seenNonces = new HashSet<>();

    private record Entry(String nonce, long timestamp) {
    }

    /**
     * Checks a message and records it if it is fresh.
     *
     * @return {@code null} when the message is fresh (and it is now recorded), or a reason when
     *         it is stale, out of window, or a replay
     */
    public synchronized String check(long timestampMillis, String nonce, long nowMillis) {
        if (nonce == null || nonce.isBlank()) {
            return "missing nonce";
        }
        long skew = Math.abs(nowMillis - timestampMillis);
        if (skew > WINDOW_MILLIS) {
            return "timestamp outside the " + (WINDOW_MILLIS / 1000) + "s window";
        }
        expire(nowMillis);
        if (seenNonces.contains(nonce)) {
            return "replayed nonce";
        }
        seen.addLast(new Entry(nonce, nowMillis));
        seenNonces.add(nonce);
        return null;
    }

    /** Drops nonces older than the window. Callers do not need to; {@link #check} does it. */
    public synchronized void expire(long nowMillis) {
        while (!seen.isEmpty() && nowMillis - seen.peekFirst().timestamp() > WINDOW_MILLIS) {
            seenNonces.remove(seen.removeFirst().nonce());
        }
    }

    /** How many nonces are currently remembered (bounded by the message rate within the window). */
    public synchronized int size() {
        return seenNonces.size();
    }
}
