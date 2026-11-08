package com.logvex.crystal.scan.world;

/**
 * The lifecycle states of the world scanner.
 *
 * <p>The scanner is a small state machine: it starts idle, initialises its bounds and route,
 * then alternates between mapping (walking the route and sampling chunks) and analysing
 * (classifying the samples it collected). It can be paused, resumed, cancelled and, when a
 * run finishes or a hard precondition fails, it lands in {@link #COMPLETED} or {@link #ERROR}.
 *
 * <p>Deliberately Minecraft-free so the transition rules can be unit tested without a client.
 */
public enum ScanState {

    /** Nothing is running; no session is active. */
    IDLE,
    /** A run was requested: bounds and route are being resolved. */
    INITIALIZING,
    /** Walking the route and sampling chunks. */
    MAPPING,
    /** Actively exploring toward the next waypoint (movement/flight engaged). */
    EXPLORING,
    /** Classifying collected samples off the client thread. */
    ANALYZING,
    /** Temporarily halted; the session and its progress are retained. */
    PAUSED,
    /** The route was completed (or the region fully mapped). */
    COMPLETED,
    /** A precondition failed or a hard error was hit; the session stopped. */
    ERROR;

    /** True while a run is in progress (including a pause). */
    public boolean running() {
        return this == INITIALIZING || this == MAPPING || this == EXPLORING || this == ANALYZING || this == PAUSED;
    }

    /** True once the run has finished, one way or the other. */
    public boolean terminal() {
        return this == COMPLETED || this == ERROR;
    }

    /** A short, lower-case label for the HUD and dashboard. */
    public String label() {
        return switch (this) {
            case IDLE -> "idle";
            case INITIALIZING -> "initialising";
            case MAPPING -> "mapping";
            case EXPLORING -> "exploring";
            case ANALYZING -> "analysing";
            case PAUSED -> "paused";
            case COMPLETED -> "completed";
            case ERROR -> "error";
        };
    }
}
