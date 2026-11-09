package com.logvex.crystal.variant;

/**
 * A capability that exists only in some builds, discovered from the feature manifest rather than
 * from a compile-time flag.
 *
 * <p>The core asks the registry whether a capability is present; it never imports the class that
 * provides it. That keeps the standard build free of administration code while letting the
 * Overseer build light up the extra screens and HUD elements.
 */
public interface ClientFeature {

    /** Stable id, used in logs and diagnostics. */
    String id();

    /** Human-readable name for the dashboard. */
    String displayName();

    /** The capability this feature provides. */
    Capability capability();

    /** Called once at client start, on the client thread, after the core is initialised. */
    void start(FeatureHost host);

    /** Called every client tick, on the client thread. Must not block. */
    default void tick() {
    }

    /** Called at client shutdown, on a best-effort basis. */
    void stop();

    /** The capability tags a feature can provide. */
    enum Capability {
        /** Remote administration of other instances (the Overseer edition). */
        REMOTE_ADMIN
    }
}
