package com.logvex.crystal.automation.task;

/**
 * The lifecycle state of an {@link AutomationTask} inside the {@link TaskQueue}.
 *
 * <p>Only {@link #COMPLETED} means the task's own success condition was verified. Every other
 * terminal state (failed, cancelled, skipped) is recorded with the reason so the history view can
 * explain what happened rather than showing a bare failure.
 */
public enum TaskState {

    /** Accepted and waiting for its delay, cooldown, resources or turn. */
    PENDING,
    /** Its {@code step} is being called this tick. */
    RUNNING,
    /** Blocked: a precondition or resource is not ready yet; it will be retried. */
    WAITING,
    /** A step failed but attempts remain; it will be retried after a backoff. */
    RETRYING,
    /** The task's success condition was reached. */
    COMPLETED,
    /** Attempts were exhausted, or the step reported an unrecoverable failure. */
    FAILED,
    /** Cancelled by the user, by an owner, or by a global stop. */
    CANCELLED,
    /** A conditional task whose condition did not hold, so it was skipped without running. */
    SKIPPED;

    /** True for a state that still occupies the queue. */
    public boolean active() {
        return this == PENDING || this == RUNNING || this == WAITING || this == RETRYING;
    }

    /** True once the task can no longer change state. */
    public boolean terminal() {
        return !active();
    }
}
