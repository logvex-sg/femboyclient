package com.logvex.crystal.automation.task;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * The shared task scheduler: a priority queue of {@link AutomationTask}s with delays, cooldowns,
 * resource arbitration, conditional gating, retry/recovery, progress reporting, cancellation and
 * a bounded history.
 *
 * <p>This is the single place every automation module schedules work, so the ordering rules -
 * priority first, then submission order - and the safety rules - a global pause, a global stop,
 * per-owner cancellation, exclusive named resources - are enforced once rather than re-invented
 * per module. It never runs a step for a task whose preconditions do not hold, and it releases a
 * task's resources on every exit path.
 *
 * <p>Minecraft-independent: the {@link TaskContext} and the tick counter are supplied by the
 * caller, so the whole policy is unit tested without a game. All methods run on the client thread;
 * the collections are synchronized so the GUI can read a snapshot during a frame.
 */
public final class TaskQueue {

    /** One scheduled task and its live runtime state. */
    public static final class Entry {
        private final AutomationTask task;
        private final long sequence;
        private final long submittedAtTick;
        private final String owner;

        private TaskState state = TaskState.PENDING;
        private long readyAtTick;
        private long startedAtTick = -1;
        private long finishedAtTick = -1;
        private int attempts;
        private int stepsDone;
        private int progressDone;
        private int progressTotal;
        private String note = "";
        private String failure = "";
        private boolean resourcesHeld;
        private final List<String> log = new ArrayList<>();

        private Entry(AutomationTask task, long sequence, long submittedAtTick) {
            this.task = task;
            this.sequence = sequence;
            this.submittedAtTick = submittedAtTick;
            this.owner = task.owner();
            this.progressTotal = task.totalSteps();
            this.readyAtTick = submittedAtTick + task.delayTicks();
        }

        public AutomationTask task() {
            return task;
        }

        public String id() {
            return task.id();
        }

        public String name() {
            return task.name();
        }

        public String owner() {
            return owner;
        }

        public TaskState state() {
            return state;
        }

        public TaskPriority priority() {
            return task.priority();
        }

        public long submittedAtTick() {
            return submittedAtTick;
        }

        public long readyAtTick() {
            return readyAtTick;
        }

        public long startedAtTick() {
            return startedAtTick;
        }

        public long finishedAtTick() {
            return finishedAtTick;
        }

        public int attempts() {
            return attempts;
        }

        public int stepsDone() {
            return stepsDone;
        }

        public int progressDone() {
            return progressDone;
        }

        public int progressTotal() {
            return progressTotal;
        }

        /** A short live status line (from the step or the queue). */
        public String note() {
            return note;
        }

        public String failure() {
            return failure;
        }

        public List<String> log() {
            return List.copyOf(log);
        }

        /** Fraction 0..1 of the task's progress, or -1 when the total is unknown. */
        public float progressFraction() {
            return progressTotal <= 0 ? -1f : Math.min(1f, (float) progressDone / progressTotal);
        }

        public boolean resourcesHeld() {
            return resourcesHeld;
        }

        private void append(String line) {
            log.add(line);
            while (log.size() > 16) {
                log.remove(0);
            }
        }
    }

    /** A finished task kept for the history view. */
    public record Record(String id, String name, String owner, TaskState outcome,
                         long submittedAtTick, long finishedAtTick, int attempts, String detail) {
        public String describe() {
            String mark = switch (outcome) {
                case COMPLETED -> "\u2713";
                case SKIPPED -> "\u21b7";
                case CANCELLED -> "\u2298";
                default -> "\u2717";
            };
            return mark + " " + name + " (" + outcome.name().toLowerCase(java.util.Locale.ROOT) + ")"
                    + (detail == null || detail.isBlank() ? "" : " - " + detail);
        }
    }

    private static final int HISTORY_CAPACITY = 128;
    /** Guards against a runaway task set: the queue refuses to hold more than this many active tasks. */
    private static final int MAX_ACTIVE = 256;

    private final List<Entry> active = new ArrayList<>();
    private final Deque<Record> history = new ArrayDeque<>();
    private final Map<String, Set<String>> resourceOwners = new HashMap<>();
    private final Set<String> pausedOwners = new HashSet<>();

    private long sequence;
    private boolean running = true;
    private boolean emergencyStopped;

    // ------------------------------------------------------------------ submission

    /**
     * Enqueues a task. A task with an id already present and active is rejected (returns empty),
     * so a module that re-submits the same task every tick cannot pile up duplicates.
     *
     * @return the created entry, or empty when the queue is paused, full, or the id is active
     */
    public synchronized Optional<Entry> submit(AutomationTask task, long nowTick) {
        if (task == null || !running) {
            return Optional.empty();
        }
        for (Entry existing : active) {
            if (existing.id().equals(task.id())) {
                return Optional.empty();
            }
        }
        if (active.size() >= MAX_ACTIVE) {
            return Optional.empty();
        }
        Entry entry = new Entry(task, sequence++, nowTick);
        entry.append("submitted");
        active.add(entry);
        return Optional.of(entry);
    }

    /** Enqueues a task, replacing any active task with the same id. */
    public synchronized Optional<Entry> resubmit(AutomationTask task, long nowTick) {
        if (task == null) {
            return Optional.empty();
        }
        cancel(task.id(), nowTick, "replaced");
        return submit(task, nowTick);
    }

    // ------------------------------------------------------------------ controls

    /** Pauses or resumes the whole queue. A paused queue starts no new tasks. */
    public synchronized void setRunning(boolean value) {
        this.running = value;
    }

    public synchronized boolean isRunning() {
        return running;
    }

    /** True once an emergency stop has been triggered; cleared by {@link #reset()}. */
    public synchronized boolean emergencyStopped() {
        return emergencyStopped;
    }

    /**
     * Pauses the queue and cancels every active task, releasing all resources. This is the
     * "emergency stop": nothing further runs until {@link #reset()}.
     */
    public synchronized int emergencyStop(long nowTick, String reason) {
        emergencyStopped = true;
        running = false;
        int cancelled = 0;
        List<Entry> snapshot = new ArrayList<>(active);
        for (Entry entry : snapshot) {
            if (entry.state.active()) {
                finish(entry, TaskState.CANCELLED, nowTick,
                        reason == null ? "emergency stop" : reason);
                cancelled++;
            }
        }
        active.clear();
        resourceOwners.clear();
        return cancelled;
    }

    /** Clears an emergency stop and re-enables the queue. */
    public synchronized void reset() {
        emergencyStopped = false;
        running = true;
    }

    /** Cancels every active task owned by {@code owner}. */
    public synchronized int cancelOwner(String owner, long nowTick, String reason) {
        if (owner == null) {
            return 0;
        }
        int cancelled = 0;
        List<Entry> snapshot = new ArrayList<>(active);
        for (Entry entry : snapshot) {
            if (entry.state.active() && entry.owner().equalsIgnoreCase(owner)) {
                finish(entry, TaskState.CANCELLED, nowTick, reason);
                active.remove(entry);
                releaseResources(entry);
                cancelled++;
            }
        }
        return cancelled;
    }

    /** Cancels a single task by id. */
    public synchronized boolean cancel(String id, long nowTick, String reason) {
        for (Entry entry : new ArrayList<>(active)) {
            if (entry.id().equals(id) && entry.state.active()) {
                finish(entry, TaskState.CANCELLED, nowTick, reason);
                active.remove(entry);
                releaseResources(entry);
                return true;
            }
        }
        return false;
    }

    /** Pauses the tasks of one owner without cancelling them (they resume on {@link #resumeOwner}). */
    public synchronized void pauseOwner(String owner) {
        if (owner != null) {
            pausedOwners.add(owner.toLowerCase(java.util.Locale.ROOT));
        }
    }

    public synchronized void resumeOwner(String owner) {
        if (owner != null) {
            pausedOwners.remove(owner.toLowerCase(java.util.Locale.ROOT));
        }
    }

    public synchronized boolean ownerPaused(String owner) {
        return owner != null && pausedOwners.contains(owner.toLowerCase(java.util.Locale.ROOT));
    }

    // ------------------------------------------------------------------ resources

    /** True when every resource is held by nobody else, or already by {@code owner}. */
    public synchronized boolean resourcesFree(String owner, Set<String> resources) {
        for (String resource : resources) {
            Set<String> holders = resourceOwners.get(resource);
            if (holders == null || holders.isEmpty()) {
                continue;
            }
            if (holders.size() == 1 && holders.contains(owner)) {
                continue;
            }
            return false;
        }
        return true;
    }

    /** Which owner currently holds {@code resource}, or empty when free. */
    public synchronized Optional<String> holderOf(String resource) {
        Set<String> holders = resourceOwners.get(resource);
        if (holders == null || holders.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(holders.iterator().next());
    }

    private synchronized boolean acquire(String owner, Set<String> resources) {
        if (!resourcesFree(owner, resources)) {
            return false;
        }
        for (String resource : resources) {
            resourceOwners.computeIfAbsent(resource, key -> new TreeSet<>()).add(owner);
        }
        return true;
    }

    private synchronized void release(String owner) {
        for (Set<String> holders : resourceOwners.values()) {
            holders.remove(owner);
        }
        resourceOwners.values().removeIf(Set::isEmpty);
    }

    private void releaseResources(Entry entry) {
        if (entry.resourcesHeld) {
            release(entry.owner());
            entry.resourcesHeld = false;
        }
    }

    // ------------------------------------------------------------------ the tick

    /**
     * Advances the queue one step. Call once per client tick.
     *
     * <p>Selection order is priority (high first) then submission order, so a survival task is
     * never starved by a cosmetic one. A selected task's conditions, delay and resources are
     * checked before its step runs; anything unmet leaves it pending and moves on.
     *
     * @return the number of task steps that ran this tick
     */
    public synchronized int tick(TaskContext context, long nowTick) {
        if (!running || context == null) {
            return 0;
        }
        List<Entry> ordered = new ArrayList<>(active);
        ordered.sort(Comparator
                .comparingInt((Entry e) -> e.priority().weight()).reversed()
                .thenComparingLong(e -> e.sequence));

        int ran = 0;
        for (Entry entry : ordered) {
            if (!entry.state.active()) {
                continue;
            }
            if (ownerPaused(entry.owner())) {
                continue;
            }
            if (runEntry(entry, context, nowTick)) {
                ran++;
            }
        }
        // Drop finished entries from the active list; they were recorded in finish().
        active.removeIf(entry -> entry.state.terminal());
        return ran;
    }

    /** @return true when the entry's step actually ran this tick */
    private boolean runEntry(Entry entry, TaskContext context, long nowTick) {
        if (entry.task.requiresWorld() && !context.worldAvailable()) {
            return false;
        }
        // Conditions: either hold, or skip the task / hold it pending.
        boolean conditionsHold = conditionsHold(entry.task, context);
        if (!conditionsHold) {
            if (entry.task.skipWhenConditionsFail() && entry.state == TaskState.PENDING) {
                finish(entry, TaskState.SKIPPED, nowTick, "condition not met");
            } else {
                entry.state = TaskState.WAITING;
                entry.note = "waiting for condition";
            }
            return false;
        }

        // Delay.
        if (nowTick < entry.readyAtTick) {
            entry.state = TaskState.PENDING;
            return false;
        }

        // Resources.
        if (!entry.resourcesHeld) {
            if (!acquire(entry.owner(), entry.task.resources())) {
                entry.state = TaskState.WAITING;
                entry.note = "waiting for " + firstHeldElsewhere(entry);
                return false;
            }
            entry.resourcesHeld = true;
        }

        if (entry.startedAtTick < 0) {
            entry.startedAtTick = nowTick;
            entry.append("started");
        }
        entry.state = TaskState.RUNNING;
        entry.attempts++;
        entry.stepsDone++;
        AutomationTask.StepResult result;
        try {
            result = entry.task.advance(context, new Sink(entry));
        } catch (RuntimeException e) {
            result = AutomationTask.StepResult.FAILED;
            entry.failure = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        applyResult(entry, result, nowTick);
        return true;
    }

    private void applyResult(Entry entry, AutomationTask.StepResult result, long nowTick) {
        switch (result) {
            case COMPLETED -> finish(entry, TaskState.COMPLETED, nowTick, "completed");
            case WAITING -> {
                entry.state = TaskState.WAITING;
                if (entry.note.isEmpty()) {
                    entry.note = "waiting";
                }
            }
            case RUNNING -> entry.state = TaskState.RUNNING;
            case FAILED -> {
                if (entry.attempts < entry.task.maxAttempts()) {
                    entry.state = TaskState.RETRYING;
                    entry.readyAtTick = nowTick + entry.task.retryBackoffTicks();
                    entry.note = "retrying (attempt " + (entry.attempts + 1) + "/"
                            + entry.task.maxAttempts() + ")";
                    entry.append(entry.note);
                } else {
                    finish(entry, TaskState.FAILED, nowTick,
                            entry.failure.isEmpty() ? "step reported failure" : entry.failure);
                }
            }
        }
    }

    private boolean conditionsHold(AutomationTask task, TaskContext context) {
        for (AutomationTask.Condition condition : task.conditions()) {
            try {
                if (!condition.test(context)) {
                    return false;
                }
            } catch (RuntimeException e) {
                return false;
            }
        }
        return true;
    }

    private String firstHeldElsewhere(Entry entry) {
        for (String resource : entry.task.resources()) {
            Optional<String> holder = holderOf(resource);
            if (holder.isPresent() && !holder.get().equalsIgnoreCase(entry.owner())) {
                return resource + " (" + holder.get() + ")";
            }
        }
        return "resources";
    }

    private void finish(Entry entry, TaskState state, long nowTick, String detail) {
        entry.state = state;
        entry.finishedAtTick = nowTick;
        if (state == TaskState.FAILED) {
            entry.failure = detail == null ? "" : detail;
        }
        entry.append(state.name().toLowerCase(java.util.Locale.ROOT) + ": " + detail);
        releaseResources(entry);
        Record record = new Record(entry.id(), entry.name(), entry.owner(), state,
                entry.submittedAtTick, nowTick, entry.attempts, detail);
        history.addLast(record);
        while (history.size() > HISTORY_CAPACITY) {
            history.removeFirst();
        }
    }

    // ------------------------------------------------------------------ views

    public synchronized List<Entry> active() {
        return new ArrayList<>(active);
    }

    /** Active tasks in the order the scheduler would run them (priority, then submission). */
    public synchronized List<Entry> activeOrdered() {
        List<Entry> ordered = new ArrayList<>(active);
        ordered.sort(Comparator
                .comparingInt((Entry e) -> e.priority().weight()).reversed()
                .thenComparingLong(e -> e.sequence));
        return ordered;
    }

    /** Recent finished tasks, oldest first. */
    public synchronized List<Record> history() {
        return new ArrayList<>(history);
    }

    public synchronized Optional<Entry> byId(String id) {
        return active.stream().filter(entry -> entry.id().equals(id)).findFirst();
    }

    /** The task the scheduler would run next, for the "current task" indicator. */
    public synchronized Optional<Entry> current() {
        return activeOrdered().stream().filter(entry -> entry.state == TaskState.RUNNING).findFirst();
    }

    public synchronized int activeCount() {
        return active.size();
    }

    public synchronized void clearHistory() {
        history.clear();
    }

    public synchronized Map<String, String> resourceHolders() {
        Map<String, String> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : resourceOwners.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                snapshot.put(entry.getKey(), String.join(",", entry.getValue()));
            }
        }
        return snapshot;
    }

    /** A one-line summary for diagnostics and the status indicator. */
    public synchronized String summary() {
        long pendingCount = active.stream().filter(e -> e.state == TaskState.PENDING).count();
        long runningCount = active.stream().filter(e -> e.state == TaskState.RUNNING).count();
        return "tasks: " + active.size() + " active (" + runningCount + " running, " + pendingCount
                + " pending), " + history.size() + " in history"
                + (emergencyStopped ? ", EMERGENCY STOP" : running ? "" : ", paused");
    }

    /** Adapts progress callbacks onto the entry so the GUI can render a live bar. */
    private static final class Sink implements AutomationTask.ProgressSink {
        private final Entry entry;

        private Sink(Entry entry) {
            this.entry = entry;
        }

        @Override
        public void progress(int done, int total) {
            entry.progressDone = done;
            if (total > 0) {
                entry.progressTotal = total;
            }
        }

        @Override
        public void note(String note) {
            entry.note = note == null ? "" : note;
        }
    }
}
