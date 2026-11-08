package com.logvex.crystal.scan.world;

/**
 * An immutable view of the scanner for the HUD and dashboard. Everything a panel needs to draw,
 * captured at one instant so the UI never reads a half-updated scanner.
 */
public record ScanSnapshot(
        ScanState state,
        String dimension,
        int exploredChunks,
        int discoveries,
        long durationMillis,
        int routeIndex,
        int routeSize,
        double progress,
        int pendingClassifications,
        int unloadedSkipped,
        int duplicates,
        String flightStatus,
        String movementStatus,
        double samplesPerSecond,
        long averageTickNanos,
        String message) {

    /** Progress as a 0..1 fraction. */
    public static double progress(int index, int size) {
        if (size <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(1, index / (double) size));
    }

    /** A compact status line for the HUD. */
    public String statusLine() {
        return state.label() + " " + Math.round(progress * 100) + "% \u00b7 " + exploredChunks
                + " chunks \u00b7 " + discoveries + " finds";
    }
}
