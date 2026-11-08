package com.logvex.crystal.scan.world;

/**
 * How strongly an observation is backed by evidence.
 *
 * <p>The mission requires the scanner to distinguish a confirmed find from an uncertain guess,
 * and to never present a prediction as a confirmation. This enum is that distinction, and it is
 * derived from how many independent signals agreed rather than from a single threshold, so a
 * verdict can be explained.
 *
 * <p>{@link #CONFIRMED} and {@link #LIKELY} are recorded findings; {@link #UNCERTAIN} is stored
 * but must be shown as a guess. {@link #REJECTED} marks an observation a user explicitly
 * dismissed.
 */
public enum VerificationStatus {

    /** Several independent signals agree; safe to report as a finding. */
    CONFIRMED("confirmed"),
    /** More than one signal agrees but not enough for confirmation. */
    LIKELY("likely"),
    /** A single weak signal, or none: stored as a guess, never reported as a find. */
    UNCERTAIN("uncertain"),
    /** A user reviewed the observation and dismissed it. */
    REJECTED("rejected");

    private final String label;

    VerificationStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** True when this status may be counted as a real discovery. */
    public boolean countsAsDiscovery() {
        return this == CONFIRMED || this == LIKELY;
    }

    /** Maps an agreeing-signal count to a status. */
    public static VerificationStatus fromSignals(int agreeing) {
        if (agreeing >= 3) {
            return CONFIRMED;
        }
        if (agreeing == 2) {
            return LIKELY;
        }
        return UNCERTAIN;
    }
}
