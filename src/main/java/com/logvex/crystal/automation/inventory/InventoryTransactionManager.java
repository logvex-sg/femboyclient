package com.logvex.crystal.automation.inventory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * The single arbiter of every inventory move in the client.
 *
 * <p>Minecraft has one cursor stack per screen handler, and the server applies slot clicks in the
 * order it receives them. If two modules click slots on the same tick the moves interleave and an
 * item can land in the wrong place or vanish into a half-applied drag. This manager prevents that
 * by running <em>at most one transaction at a time</em>: a module submits an
 * {@link InventoryTransaction}, the manager runs exactly one step per tick, waits for the server
 * to confirm the step (via the step's own verification predicate) before advancing, and releases
 * the slot for the next transaction only when the whole transaction is done, cancelled or timed
 * out.
 *
 * <p>This is why every inventory module must go through here: it is the one place that guarantees
 * the client never has two in-flight moves. It is Minecraft-independent - a transaction's steps
 * are plain runnables with verification predicates - so the whole sequencing policy is unit tested
 * without a game. All methods run on the client thread; reads are synchronized so the GUI can
 * snapshot during a frame.
 */
public final class InventoryTransactionManager {

    /** Why a transaction stopped. */
    public enum Outcome {
        COMPLETED,
        FAILED,
        CANCELLED,
        TIMEOUT
    }

    /** A finished transaction, for the history view. */
    public record Record(String owner, String description, Outcome outcome, int stepsDone,
                         int stepsTotal, long startedAtMillis, long finishedAtMillis, String detail) {
        public String describe() {
            return outcome.name().toLowerCase(java.util.Locale.ROOT) + " " + description
                    + " (" + stepsDone + "/" + stepsTotal + ")"
                    + (detail == null || detail.isBlank() ? "" : " - " + detail);
        }
    }

    /** One transaction's live progress. */
    public static final class Active {
        private final InventoryTransaction transaction;
        private long startedAtMillis;
        private int index;
        private int stepsRun;
        private boolean stepIssued;
        private long stepIssuedAtMillis;

        private Active(InventoryTransaction transaction, long nowMillis) {
            this.transaction = transaction;
            this.startedAtMillis = nowMillis;
        }

        public InventoryTransaction transaction() {
            return transaction;
        }

        public int stepsRun() {
            return stepsRun;
        }

        public int stepsTotal() {
            return transaction.size();
        }

        public String currentStep() {
            return index < transaction.steps().size()
                    ? transaction.steps().get(index).description() : "done";
        }

        public float fraction() {
            return transaction.size() == 0 ? 1f : (float) index / transaction.size();
        }
    }

    private static final int HISTORY_CAPACITY = 64;
    /** How long to wait for one step's verification before giving the transaction up as stuck. */
    private static final long STEP_TIMEOUT_MILLIS = 750L;

    private final List<Active> pending = new ArrayList<>();
    private final Deque<Record> history = new ArrayDeque<>();

    private Active running;
    private String suspendedOwner;
    private int submittedTotal;
    private int completedTotal;
    private int failedTotal;
    private int cancelledTotal;
    private int timedOutTotal;

    /**
     * Submits a transaction. Transactions run one at a time in submission order; a module may hold
     * at most one pending transaction, so re-submitting while one is queued is refused.
     *
     * @return true when the transaction was accepted
     */
    public synchronized boolean submit(InventoryTransaction transaction) {
        if (transaction == null || transaction.size() == 0 || suspendedOwner != null) {
            return false;
        }
        if (running != null && running.transaction().owner().equalsIgnoreCase(transaction.owner())) {
            return false;
        }
        for (Active active : pending) {
            if (active.transaction().owner().equalsIgnoreCase(transaction.owner())) {
                return false;
            }
        }
        pending.add(new Active(transaction, System.currentTimeMillis()));
        submittedTotal++;
        return true;
    }

    /**
     * Advances the manager one step. Call once per client tick.
     *
     * @return true when a step was issued this tick
     */
    public synchronized boolean tick(long nowMillis) {
        if (running == null) {
            startNext(nowMillis);
        }
        if (running == null) {
            return false;
        }
        InventoryTransaction transaction = running.transaction();
        if (nowMillis - running.startedAtMillis >= transaction.timeoutMillis()) {
            finish(Outcome.TIMEOUT, nowMillis, "exceeded " + transaction.timeoutMillis() + "ms");
            return false;
        }
        if (running.stepIssued) {
            // Wait for the previous step's effect to be observable before issuing the next. At
            // most one click leaves the client per tick, which is what keeps the server in step.
            InventoryTransaction.Step issued = transaction.steps().get(running.index);
            if (issued.verify()) {
                running.index++;
                running.stepIssued = false;
            } else if (nowMillis - running.stepIssuedAtMillis >= STEP_TIMEOUT_MILLIS) {
                finish(Outcome.FAILED, nowMillis, "step '" + issued.description() + "' not confirmed");
                return false;
            } else {
                return false;
            }
        }
        if (running.index >= transaction.steps().size()) {
            finish(Outcome.COMPLETED, nowMillis, "all steps verified");
            return false;
        }
        InventoryTransaction.Step step = transaction.steps().get(running.index);
        try {
            step.run();
        } catch (RuntimeException e) {
            finish(Outcome.FAILED, nowMillis, e.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        }
        running.stepIssued = true;
        running.stepIssuedAtMillis = nowMillis;
        running.stepsRun++;
        return true;
    }

    private void startNext(long nowMillis) {
        if (suspendedOwner != null) {
            return;
        }
        if (pending.isEmpty()) {
            return;
        }
        running = pending.remove(0);
        running.startedAtMillis = nowMillis;
    }

    /** Cancels the running transaction and every pending one owned by {@code owner}. */
    public synchronized int cancelOwner(String owner, long nowMillis) {
        if (owner == null) {
            return 0;
        }
        int cancelled = 0;
        if (running != null && running.transaction().owner().equalsIgnoreCase(owner)) {
            finish(Outcome.CANCELLED, nowMillis, "cancelled by owner");
            cancelled++;
        }
        for (Active active : new ArrayList<>(pending)) {
            if (active.transaction().owner().equalsIgnoreCase(owner)) {
                pending.remove(active);
                record(new Record(active.transaction().owner(), active.transaction().description(),
                        Outcome.CANCELLED, active.stepsRun, active.stepsTotal(),
                        active.startedAtMillis, nowMillis, "cancelled by owner"));
                cancelledTotal++;
                cancelled++;
            }
        }
        return cancelled;
    }

    /** Cancels everything and stops accepting submissions until {@link #resume()}. */
    public synchronized int cancelAll(long nowMillis, String reason) {
        int cancelled = 0;
        if (running != null) {
            finish(Outcome.CANCELLED, nowMillis, reason);
            cancelled++;
        }
        for (Active active : new ArrayList<>(pending)) {
            pending.remove(active);
            record(new Record(active.transaction().owner(), active.transaction().description(),
                    Outcome.CANCELLED, active.stepsRun, active.stepsTotal(),
                    active.startedAtMillis, nowMillis, reason));
            cancelledTotal++;
            cancelled++;
        }
        suspendedOwner = reason == null ? "cancelled" : reason;
        return cancelled;
    }

    /** Re-enables the manager after {@link #cancelAll}. */
    public synchronized void resume() {
        suspendedOwner = null;
    }

    public synchronized boolean isSuspended() {
        return suspendedOwner != null;
    }

    /** True when a transaction is running or queued for {@code owner}. */
    public synchronized boolean isActive(String owner) {
        if (owner == null) {
            return false;
        }
        if (running != null && running.transaction().owner().equalsIgnoreCase(owner)) {
            return true;
        }
        for (Active active : pending) {
            if (active.transaction().owner().equalsIgnoreCase(owner)) {
                return true;
            }
        }
        return false;
    }

    public synchronized Optional<Active> running() {
        return Optional.ofNullable(running);
    }

    /** True while any transaction is in flight; a module should not act when this is true. */
    public synchronized boolean busy() {
        return running != null || !pending.isEmpty();
    }

    public synchronized int queuedCount() {
        return pending.size() + (running == null ? 0 : 1);
    }

    public synchronized List<Record> history() {
        return new ArrayList<>(history);
    }

    public synchronized void clearHistory() {
        history.clear();
    }

    private void finish(Outcome outcome, long nowMillis, String detail) {
        if (running == null) {
            return;
        }
        Active active = running;
        running = null;
        switch (outcome) {
            case COMPLETED -> completedTotal++;
            case FAILED -> failedTotal++;
            case CANCELLED -> cancelledTotal++;
            case TIMEOUT -> timedOutTotal++;
        }
        record(new Record(active.transaction().owner(), active.transaction().description(),
                outcome, active.stepsRun, active.stepsTotal(), active.startedAtMillis, nowMillis, detail));
    }

    private void record(Record record) {
        history.addLast(record);
        while (history.size() > HISTORY_CAPACITY) {
            history.removeFirst();
        }
    }

    public synchronized int submittedTotal() {
        return submittedTotal;
    }

    public synchronized int completedTotal() {
        return completedTotal;
    }

    public synchronized int failedTotal() {
        return failedTotal;
    }

    public synchronized int cancelledTotal() {
        return cancelledTotal;
    }

    public synchronized int timedOutTotal() {
        return timedOutTotal;
    }

    /** A one-line summary for diagnostics. */
    public synchronized String summary() {
        return "inventory: " + (running == null ? "idle" : "running " + running.transaction().description())
                + ", " + pending.size() + " queued, " + completedTotal + " done, "
                + failedTotal + " failed, " + timedOutTotal + " timed out";
    }
}
