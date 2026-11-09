package com.logvex.crystal.remote.controller;

import com.logvex.crystal.remote.protocol.CommandResult;
import com.logvex.crystal.remote.protocol.CommandStatus;
import com.logvex.crystal.remote.protocol.RemoteAction;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A bounded, newest-first history of every command the controller has issued, with each
 * instance's individual result.
 *
 * <p>A bulk command is one {@link Entry} containing one {@link Outcome} per targeted instance, so
 * the dashboard can show "applied to 7 of 9" with the two failures spelled out. Keeping it
 * bounded means a long session cannot grow memory without limit, and the history is separate from
 * the {@link com.logvex.crystal.remote.security.AuditLog} because it is a user-facing convenience
 * (re-run the last command) rather than a security record.
 *
 * <p>Thread safe. Minecraft-independent and unit testable.
 */
public final class CommandHistory {

    /** One instance's outcome within a command. */
    public record Outcome(String instanceId, String instanceName, CommandStatus status, String message,
                          long completedAtMillis) {
        public boolean succeeded() {
            return status.succeeded();
        }
    }

    /** One command and the outcomes of every instance it targeted. */
    public static final class Entry {
        private final String requestId;
        private final RemoteAction action;
        private final String summary;
        private final long issuedAtMillis;
        private final List<Outcome> outcomes = new ArrayList<>();

        Entry(String requestId, RemoteAction action, String summary, long issuedAtMillis) {
            this.requestId = requestId;
            this.action = action;
            this.summary = summary == null ? "" : summary;
            this.issuedAtMillis = issuedAtMillis;
        }

        public String requestId() {
            return requestId;
        }

        public RemoteAction action() {
            return action;
        }

        public String summary() {
            return summary;
        }

        public long issuedAtMillis() {
            return issuedAtMillis;
        }

        public synchronized List<Outcome> outcomes() {
            return List.copyOf(outcomes);
        }

        /**
         * Records an outcome. A result that arrives for an instance already present (as the
         * PENDING outcome added when the command was sent) replaces it, so one instance has at
         * most one outcome and {@link #complete()} can become true. A late duplicate is ignored.
         */
        synchronized void add(Outcome outcome) {
            for (int i = 0; i < outcomes.size(); i++) {
                if (outcomes.get(i).instanceId().equals(outcome.instanceId())) {
                    if (outcomes.get(i).status() == CommandStatus.PENDING) {
                        outcomes.set(i, outcome);
                    }
                    return;
                }
            }
            outcomes.add(outcome);
        }

        public synchronized int okCount() {
            int n = 0;
            for (Outcome o : outcomes) {
                if (o.status() == CommandStatus.OK) {
                    n++;
                }
            }
            return n;
        }

        public synchronized int unsupportedCount() {
            int n = 0;
            for (Outcome o : outcomes) {
                if (o.status() == CommandStatus.UNSUPPORTED) {
                    n++;
                }
            }
            return n;
        }

        public synchronized int failureCount() {
            int n = 0;
            for (Outcome o : outcomes) {
                if (!o.status().succeeded() && o.status() != CommandStatus.UNSUPPORTED) {
                    n++;
                }
            }
            return n;
        }

        public synchronized boolean complete() {
            for (Outcome o : outcomes) {
                if (!o.status().terminal()) {
                    return false;
                }
            }
            return true;
        }

        /** A one-line roll-up, e.g. {@code "enable_module: 7 ok, 1 unsupported, 1 failed"}. */
        public synchronized String describe() {
            return action.wire() + ": " + okCount() + " ok, " + unsupportedCount() + " unsupported, "
                    + failureCount() + " failed";
        }
    }

    private final int capacity;
    private final Deque<Entry> entries = new ArrayDeque<>();

    public CommandHistory(int capacity) {
        this.capacity = Math.max(8, capacity);
    }

    public static CommandHistory standard() {
        return new CommandHistory(200);
    }

    public synchronized Entry begin(String requestId, RemoteAction action, String summary,
                                    long issuedAtMillis) {
        Entry entry = new Entry(requestId, action, summary, issuedAtMillis);
        entries.addFirst(entry);
        while (entries.size() > capacity) {
            entries.removeLast();
        }
        return entry;
    }

    /** Appends a result to the matching entry, if it is still in the history. */
    public synchronized void record(CommandResult result, String instanceName) {
        for (Entry entry : entries) {
            if (entry.requestId().equals(result.requestId())) {
                entry.add(new Outcome(result.instanceId(), instanceName, result.status(),
                        result.message(), result.completedAtMillis()));
                return;
            }
        }
    }

    /** The most recent entries, newest first. */
    public synchronized List<Entry> recent(int limit) {
        List<Entry> all = new ArrayList<>(entries);
        if (limit > 0 && all.size() > limit) {
            return List.copyOf(all.subList(0, limit));
        }
        return List.copyOf(all);
    }

    public synchronized Entry byRequestId(String requestId) {
        for (Entry entry : entries) {
            if (entry.requestId().equals(requestId)) {
                return entry;
            }
        }
        return null;
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized void clear() {
        entries.clear();
    }
}
