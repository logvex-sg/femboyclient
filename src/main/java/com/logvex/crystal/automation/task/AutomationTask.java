package com.logvex.crystal.automation.task;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * One unit of scheduled work: a named {@link Step} plus the policy that governs when and how it
 * runs - priority, owner, required resources, start delay, cooldown, retry budget and whether a
 * world must be loaded.
 *
 * <p>A task is a value object configured once and then handed to a {@link TaskQueue}, which owns
 * its runtime state (the task itself stays immutable apart from the queue-managed fields). It is
 * deliberately declarative: a task never schedules itself, never loops on the client thread and
 * never acquires a lock directly - it states what it needs and the queue decides.
 *
 * <p>Build one with {@link #builder(String, String, Step)}.
 */
public final class AutomationTask {

    /** What a step did this tick, which drives the task's next state. */
    public enum StepResult {
        /** Made progress but is not finished; call again next tick. */
        RUNNING,
        /** The success condition was reached. */
        COMPLETED,
        /** A recoverable failure; the queue will retry within the attempt budget. */
        FAILED,
        /** A precondition is not ready; the task yields and is retried later without penalty. */
        WAITING
    }

    /** Receives progress updates from a step so the GUI can show a bar. */
    public interface ProgressSink {
        /** Records absolute progress ({@code done} of {@code total}); total &le; 0 means unknown. */
        void progress(int done, int total);

        /** Records a short human-readable status line (e.g. "moving stack 2/5"). */
        void note(String note);
    }

    /**
     * One step of a task. Called on the client thread, at most once per tick, only while the
     * queue holds the task's resources. It must not block.
     */
    @FunctionalInterface
    public interface Step {
        StepResult step(TaskContext context, ProgressSink progress);
    }

    /** A precondition evaluated before a task starts; a task with several needs all to hold. */
    @FunctionalInterface
    public interface Condition {
        boolean test(TaskContext context);
    }

    private final String id;
    private final String name;
    private final String description;
    private final String owner;
    private final TaskPriority priority;
    private final Step step;
    private final Set<String> resources;
    private final Set<Condition> conditions;
    private final long delayTicks;
    private final long cooldownTicks;
    private final int maxAttempts;
    private final long retryBackoffTicks;
    private final boolean requiresWorld;
    private final int totalSteps;
    private final boolean skipWhenConditionsFail;

    private AutomationTask(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.description = builder.description;
        this.owner = builder.owner;
        this.priority = builder.priority;
        this.step = builder.step;
        this.resources = Set.copyOf(builder.resources);
        this.conditions = Set.copyOf(builder.conditions);
        this.delayTicks = Math.max(0, builder.delayTicks);
        this.cooldownTicks = Math.max(0, builder.cooldownTicks);
        this.maxAttempts = Math.max(1, builder.maxAttempts);
        this.retryBackoffTicks = Math.max(0, builder.retryBackoffTicks);
        this.requiresWorld = builder.requiresWorld;
        this.totalSteps = builder.totalSteps;
        this.skipWhenConditionsFail = builder.skipWhenConditionsFail;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    /** The module or subsystem that owns this task, used for cancellation and reporting. */
    public String owner() {
        return owner;
    }

    public TaskPriority priority() {
        return priority;
    }

    public Set<String> resources() {
        return resources;
    }

    public Set<Condition> conditions() {
        return conditions;
    }

    public long delayTicks() {
        return delayTicks;
    }

    public long cooldownTicks() {
        return cooldownTicks;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public long retryBackoffTicks() {
        return retryBackoffTicks;
    }

    public boolean requiresWorld() {
        return requiresWorld;
    }

    /** The expected number of steps for a progress bar, or 0 when unknown. */
    public int totalSteps() {
        return totalSteps;
    }

    /**
     * When true, a task whose {@link Condition}s do not hold is {@link TaskState#SKIPPED} rather
     * than held as {@link TaskState#WAITING}. This is how a conditional task expresses "do this
     * only if X, otherwise drop it" as opposed to "do this once X becomes true".
     */
    public boolean skipWhenConditionsFail() {
        return skipWhenConditionsFail;
    }

    /** Runs the step. Package-visible so only the queue can invoke it. */
    StepResult advance(TaskContext context, ProgressSink progress) {
        return step.step(context, progress);
    }

    /** A one-line summary for the queue viewer and diagnostics. */
    public String describe() {
        return name + " [" + priority.label() + "]" + (owner.isEmpty() ? "" : " (" + owner + ")");
    }

    public static Builder builder(String id, String name, Step step) {
        return new Builder(id, name, step);
    }

    /** Fluent builder; every field has a safe default so a task needs only an id, name and step. */
    public static final class Builder {
        private final String id;
        private final String name;
        private final Step step;
        private String description = "";
        private String owner = "";
        private TaskPriority priority = TaskPriority.NORMAL;
        private final Set<String> resources = new LinkedHashSet<>();
        private final Set<Condition> conditions = new LinkedHashSet<>();
        private long delayTicks;
        private long cooldownTicks;
        private int maxAttempts = 1;
        private long retryBackoffTicks;
        private boolean requiresWorld = true;
        private int totalSteps;
        private boolean skipWhenConditionsFail;

        private Builder(String id, String name, Step step) {
            this.id = Objects.requireNonNull(id, "id");
            this.name = Objects.requireNonNull(name, "name");
            this.step = Objects.requireNonNull(step, "step");
        }

        public Builder description(String value) {
            this.description = value == null ? "" : value;
            return this;
        }

        public Builder owner(String value) {
            this.owner = value == null ? "" : value;
            return this;
        }

        public Builder priority(TaskPriority value) {
            this.priority = value == null ? TaskPriority.NORMAL : value;
            return this;
        }

        /** Declares a named resource the queue must hold for this task while it runs. */
        public Builder requiresResource(String resource) {
            if (resource != null && !resource.isBlank()) {
                resources.add(resource.trim().toLowerCase(java.util.Locale.ROOT));
            }
            return this;
        }

        /** Declares a precondition that must hold before the task starts. */
        public Builder when(Condition condition) {
            if (condition != null) {
                conditions.add(condition);
            }
            return this;
        }

        public Builder delayTicks(long value) {
            this.delayTicks = value;
            return this;
        }

        public Builder cooldownTicks(long value) {
            this.cooldownTicks = value;
            return this;
        }

        public Builder maxAttempts(int value) {
            this.maxAttempts = value;
            return this;
        }

        public Builder retryBackoffTicks(long value) {
            this.retryBackoffTicks = value;
            return this;
        }

        public Builder requiresWorld(boolean value) {
            this.requiresWorld = value;
            return this;
        }

        public Builder totalSteps(int value) {
            this.totalSteps = value;
            return this;
        }

        /** When true, unmet conditions skip the task instead of holding it pending. */
        public Builder skipWhenConditionsFail(boolean value) {
            this.skipWhenConditionsFail = value;
            return this;
        }

        public AutomationTask build() {
            return new AutomationTask(this);
        }
    }
}
