package com.logvex.crystal.scan.world;

/**
 * An immutable snapshot of the scanner's tunables, applied atomically so a change made in the
 * GUI mid-run cannot tear a scan.
 *
 * <p>All sizes are in the units named: chunks for the scan area, blocks for the vertical
 * sampling window and the route step, ticks for cadence. The values are deliberately bounded
 * by the module's settings, but the record clamps defensively so a hand-edited config cannot
 * produce a nonsensical snapshot.
 */
public record ScanConfig(
        int radiusChunks,
        int verticalRange,
        int sampleStep,
        int maxChunksPerTick,
        int routeStepBlocks,
        boolean useFlight,
        boolean classifyOffThread,
        int observationCap) {

    /** A conservative default profile. */
    public static ScanConfig defaults() {
        return new ScanConfig(8, 24, 4, 8, 32, true, true, 4000);
    }

    public ScanConfig {
        radiusChunks = clamp(radiusChunks, 1, 32);
        verticalRange = clamp(verticalRange, 1, 128);
        sampleStep = clamp(sampleStep, 1, 16);
        maxChunksPerTick = clamp(maxChunksPerTick, 1, 64);
        routeStepBlocks = clamp(routeStepBlocks, 4, 256);
        observationCap = clamp(observationCap, 64, 20000);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
