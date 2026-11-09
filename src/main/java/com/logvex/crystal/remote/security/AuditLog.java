package com.logvex.crystal.remote.security;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A bounded, append-only record of every security-relevant event: pairing, authentication
 * attempts (successful and not), permission denials, revoked tokens, rate-limit trips, command
 * dispatches and results.
 *
 * <p>It exists so that "who did what to which instance, and when" can be answered after the fact.
 * It is deliberately bounded (the oldest entries fall off) so a long-running client cannot grow
 * without limit, and it never stores a secret — only instance ids, action names, outcomes and
 * timestamps. A listener lets the dashboard mirror the log live without polling.
 *
 * <p>Thread safe: the controller and each agent connection append from different threads.
 * Minecraft-independent and unit testable.
 */
public final class AuditLog {

    /** The kinds of event recorded. */
    public enum Kind {
        PAIRING_CREATED,
        PAIRING_ACCEPTED,
        PAIRING_REJECTED,
        AUTH_SUCCESS,
        AUTH_FAILURE,
        TOKEN_REVOKED,
        PERMISSION_DENIED,
        ALLOWLIST_DENIED,
        RATE_LIMITED,
        REPLAY_REJECTED,
        COMMAND_SENT,
        COMMAND_RESULT,
        AGENT_CONNECTED,
        AGENT_DISCONNECTED,
        SESSION_EXPIRED,
        CONFIG_CHANGED
    }

    /** One audit entry. {@code severity} is 0=info, 1=notice, 2=warning. */
    public record Entry(long timestampMillis, Kind kind, String instanceId, String detail,
                        int severity) {
        public Entry {
            instanceId = instanceId == null ? "" : instanceId;
            detail = detail == null ? "" : detail;
        }

        public String describe() {
            String who = instanceId.isBlank() ? "-" : instanceId;
            return "[" + kind + "] " + who + ": " + detail;
        }
    }

    private final int capacity;
    private final Deque<Entry> entries = new ArrayDeque<>();
    private volatile java.util.function.Consumer<Entry> listener;

    public AuditLog(int capacity) {
        this.capacity = Math.max(16, capacity);
    }

    /** A log sized for a desktop client: the last 500 events. */
    public static AuditLog standard() {
        return new AuditLog(500);
    }

    public void setListener(java.util.function.Consumer<Entry> listener) {
        this.listener = listener;
    }

    public synchronized void record(Kind kind, String instanceId, String detail) {
        record(kind, instanceId, detail, 0);
    }

    public synchronized void record(Kind kind, String instanceId, String detail, int severity) {
        Entry entry = new Entry(System.currentTimeMillis(), kind, instanceId, detail, severity);
        entries.addLast(entry);
        while (entries.size() > capacity) {
            entries.removeFirst();
        }
        java.util.function.Consumer<Entry> l = listener;
        if (l != null) {
            try {
                l.accept(entry);
            } catch (RuntimeException ignored) {
                // A broken listener must never break an audited operation.
            }
        }
    }

    /** A snapshot of the log, newest first. */
    public synchronized List<Entry> recent(int limit) {
        List<Entry> all = new ArrayList<>(entries);
        java.util.Collections.reverse(all);
        if (limit > 0 && all.size() > limit) {
            return List.copyOf(all.subList(0, limit));
        }
        return List.copyOf(all);
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized void clear() {
        entries.clear();
    }
}
