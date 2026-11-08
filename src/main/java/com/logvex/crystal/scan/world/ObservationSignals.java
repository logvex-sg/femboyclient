package com.logvex.crystal.scan.world;

import java.util.ArrayList;
import java.util.List;

/**
 * The independent evidence signals behind an observation.
 *
 * <p>Each field is a signal that can stand alone, and {@link #agreeingSignals()} counts how many
 * point the same way. {@link VerificationStatus} is derived from that count, so a classification
 * can always be explained rather than asserted. This mirrors the reference client's
 * "N of M signals must agree" rule, applied to observations instead of chunk age.
 *
 * <p>All signals are read from data the client already received; none requires a server request.
 *
 * @param builtDensity     fraction of sampled blocks that were man-made building blocks
 * @param storageCount     storage containers (chests, barrels, shulkers) in the chunk
 * @param spawnerCount     mob spawners in the chunk
 * @param cropCount        crop-like blocks in the chunk
 * @param redstoneCount    redstone components in the chunk
 * @param lightCount       light-emitting blocks in the chunk
 * @param oreCount         ore blocks in the chunk
 * @param naturalVeg       logs plus leaves, the natural-vegetation signal
 * @param flowingLiquids   true when flowing (not source) liquid was seen: proof of block updates
 * @param inhabitedTicks   the chunk's inhabited time, or -1 when unknown
 * @param lightComplete    whether the server sent a populated light layer, or null when unknown
 */
public record ObservationSignals(
        double builtDensity,
        int storageCount,
        int spawnerCount,
        int cropCount,
        int redstoneCount,
        int lightCount,
        int oreCount,
        int naturalVeg,
        boolean flowingLiquids,
        long inhabitedTicks,
        Boolean lightComplete) {

    /** How many signals point at "player activity happened here". */
    public int agreeingSignals() {
        int count = 0;
        if (builtDensity >= 0.15) {
            count++;
        }
        if (storageCount > 0) {
            count++;
        }
        if (spawnerCount > 0) {
            count++;
        }
        if (cropCount > 0) {
            count++;
        }
        if (redstoneCount > 0) {
            count++;
        }
        if (lightCount > 0) {
            count++;
        }
        return count;
    }

    /**
     * True when the chunk looks freshly generated: zero or near-zero inhabited time with no
     * player-made blocks. Reported as a signal, never as a finding on its own.
     */
    public boolean looksFresh() {
        boolean unvisited = inhabitedTicks >= 0 && inhabitedTicks <= 1;
        boolean untouched = builtDensity < 0.02 && storageCount == 0 && redstoneCount == 0;
        boolean dark = Boolean.FALSE.equals(lightComplete);
        return (unvisited && untouched) || (dark && untouched);
    }

    /** True when anything at all was observed worth recording. */
    public boolean anyEvidence() {
        return agreeingSignals() > 0 || naturalVeg > 0 || oreCount > 0 || flowingLiquids;
    }

    /** Human-readable list of the signals that fired, for the dashboard and AI summaries. */
    public List<String> describe() {
        List<String> parts = new ArrayList<>();
        if (builtDensity >= 0.15) {
            parts.add("dense building blocks");
        }
        if (storageCount > 0) {
            parts.add(storageCount + " storage container(s)");
        }
        if (spawnerCount > 0) {
            parts.add(spawnerCount + " spawner(s)");
        }
        if (cropCount > 0) {
            parts.add(cropCount + " crop block(s)");
        }
        if (redstoneCount > 0) {
            parts.add(redstoneCount + " redstone component(s)");
        }
        if (lightCount > 0) {
            parts.add(lightCount + " light source(s)");
        }
        if (naturalVeg > 0) {
            parts.add(naturalVeg + " natural vegetation block(s)");
        }
        if (oreCount > 0) {
            parts.add(oreCount + " ore block(s)");
        }
        if (flowingLiquids) {
            parts.add("flowing liquids");
        }
        if (looksFresh()) {
            parts.add("freshly generated terrain");
        }
        return parts;
    }
}
