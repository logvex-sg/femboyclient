package com.logvex.crystal.scan.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Groups nearby discoveries into clusters and ranks them, so the exploration agent can visit the
 * most promising area once instead of walking to each chunk in turn.
 *
 * <p>This is the idea the reference base-hunting client uses for its target queue, applied to
 * LOGVEX observations instead of raw new chunks: adjacent observations collapse into a single
 * flyable target at the cluster centre, targets are scored on size, distance and whether anything
 * notable was found there, and the chosen targets are ordered into a greedy nearest-neighbour tour
 * so a route does not criss-cross itself.
 *
 * <p>When nothing new has been found for a while the clusterer emits a single <em>frontier</em>
 * waypoint beyond the furthest known discovery, clamped to the scan bounds, so exploration keeps
 * moving outward rather than re-treading ground. Everything here is pure integer/double maths over
 * positions, so it is fully unit testable without a world.
 */
public final class DiscoveryClusterer {

    /** How long without progress before a frontier waypoint is emitted. */
    public static final long FRONTIER_AFTER_MILLIS = 90_000L;
    /** How far beyond the furthest discovery a frontier waypoint is pushed, in blocks. */
    public static final int FRONTIER_STEP_BLOCKS = 512;

    /** A group of adjacent observations reduced to one visitable target. */
    public record Cluster(int centreBlockX, int centreBlockZ, int count, double score, String reason) {
    }

    private final Set<Long> visited = new HashSet<>();
    private long lastProgressAt;

    public DiscoveryClusterer() {
        this.lastProgressAt = System.currentTimeMillis();
    }

    /** Forgets all visited targets and resets the progress clock. */
    public void reset() {
        visited.clear();
        lastProgressAt = System.currentTimeMillis();
    }

    /** Records that something was found, which suppresses frontier expansion. */
    public void noteProgress() {
        lastProgressAt = System.currentTimeMillis();
    }

    /** Records that a target was visited, so it is never chosen again. */
    public void markVisited(int blockX, int blockZ) {
        visited.add(key(blockX, blockZ));
    }

    /** True when a target has already been visited. */
    public boolean wasVisited(int blockX, int blockZ) {
        return visited.contains(key(blockX, blockZ));
    }

    /** How long since the last recorded progress, in milliseconds. */
    public long millisSinceProgress() {
        return System.currentTimeMillis() - lastProgressAt;
    }

    /** How many distinct targets have been visited. */
    public int visitedCount() {
        return visited.size();
    }

    /**
     * Groups observation positions into clusters of adjacent chunks using a flood fill.
     *
     * @param positions  packed chunk keys (see {@link ChunkKey})
     * @param mergeRadius Chebyshev radius, in chunks, at which two positions are the same cluster
     * @return clusters as lists of packed chunk keys
     */
    public static List<List<Long>> cluster(Set<Long> positions, int mergeRadius) {
        Set<Long> remaining = new LinkedHashSet<>(positions);
        List<List<Long>> clusters = new ArrayList<>();
        int radius = Math.max(1, mergeRadius);

        while (!remaining.isEmpty()) {
            long seed = remaining.iterator().next();
            remaining.remove(seed);

            List<Long> cluster = new ArrayList<>();
            List<Long> queue = new ArrayList<>();
            queue.add(seed);

            while (!queue.isEmpty()) {
                long current = queue.remove(queue.size() - 1);
                cluster.add(current);

                int cx = ChunkKey.x(current);
                int cz = ChunkKey.z(current);
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (dx == 0 && dz == 0) {
                            continue;
                        }
                        long neighbour = ChunkKey.of(cx + dx, cz + dz);
                        if (remaining.remove(neighbour)) {
                            queue.add(neighbour);
                        }
                    }
                }
            }
            clusters.add(cluster);
        }
        return clusters;
    }

    /**
     * Turns observation positions into a ranked list of visitable targets.
     *
     * <p>Score: {@code count * 10} for the size of the cluster, minus distance/128 so a nearer
     * cluster beats an equally sized distant one, plus a fixed bonus when a cluster contains a
     * "notable" observation (anything other than natural terrain). Visited clusters are dropped.
     *
     * @param positions      packed chunk keys of the discoveries
     * @param notable        packed chunk keys that count as notable (may be empty)
     * @param mergeRadius    Chebyshev cluster radius, in chunks
     * @param fromBlockX     the player's x, for distance scoring
     * @param fromBlockZ     the player's z, for distance scoring
     * @param maxTargets     the most targets to return
     */
    public List<Cluster> buildTargets(Set<Long> positions, Set<Long> notable, int mergeRadius,
                                      int fromBlockX, int fromBlockZ, int maxTargets) {
        List<Cluster> targets = new ArrayList<>();

        for (List<Long> group : cluster(positions, mergeRadius)) {
            long sumX = 0;
            long sumZ = 0;
            boolean anyNotable = false;
            for (long key : group) {
                sumX += ChunkKey.x(key);
                sumZ += ChunkKey.z(key);
                if (notable != null && notable.contains(key)) {
                    anyNotable = true;
                }
            }
            int size = group.size();
            // Average in chunk space then convert, so the centre always lands inside the cluster.
            int centreBlockX = (int) (sumX / size) * 16 + 8;
            int centreBlockZ = (int) (sumZ / size) * 16 + 8;

            if (wasVisited(centreBlockX, centreBlockZ)) {
                continue;
            }

            double distance = Math.hypot(centreBlockX - fromBlockX, centreBlockZ - fromBlockZ);
            double score = size * 10.0 - distance / 128.0;
            String reason = size == 1 ? "single discovery" : size + " adjacent discoveries";
            if (anyNotable) {
                score += 250.0;
                reason += " (notable structure)";
            }
            targets.add(new Cluster(centreBlockX, centreBlockZ, size, score, reason));
        }

        targets.sort(Comparator.comparingDouble(Cluster::score).reversed());
        if (targets.size() > maxTargets) {
            return new ArrayList<>(targets.subList(0, maxTargets));
        }
        return targets;
    }

    /**
     * Reorders targets into a greedy nearest-neighbour tour starting from the player, so a route
     * does not cross back over itself.
     */
    public static List<Cluster> tour(List<Cluster> targets, int fromBlockX, int fromBlockZ) {
        List<Cluster> remaining = new ArrayList<>(targets);
        List<Cluster> ordered = new ArrayList<>(remaining.size());
        int currentX = fromBlockX;
        int currentZ = fromBlockZ;

        while (!remaining.isEmpty()) {
            int bestIndex = 0;
            double bestDistance = Double.MAX_VALUE;
            for (int i = 0; i < remaining.size(); i++) {
                Cluster candidate = remaining.get(i);
                double distance = Math.hypot(candidate.centreBlockX() - currentX,
                        candidate.centreBlockZ() - currentZ);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestIndex = i;
                }
            }
            Cluster next = remaining.remove(bestIndex);
            ordered.add(next);
            currentX = next.centreBlockX();
            currentZ = next.centreBlockZ();
        }
        return ordered;
    }

    /**
     * A frontier waypoint pushed beyond the furthest known discovery, clamped to {@code bounds}.
     *
     * @return a frontier cluster, or {@code null} when there is nothing to expand from or the
     *         frontier would fall outside the bounds
     */
    public Cluster frontier(Set<Long> positions, ScanBounds bounds, int fromBlockX, int fromBlockZ) {
        if (positions.isEmpty() || bounds == null) {
            return null;
        }
        int furthestX = fromBlockX;
        int furthestZ = fromBlockZ;
        double furthest = -1;
        for (long key : positions) {
            int bx = (ChunkKey.x(key) << 4) + 8;
            int bz = (ChunkKey.z(key) << 4) + 8;
            double distance = Math.hypot(bx - fromBlockX, bz - fromBlockZ);
            if (distance > furthest) {
                furthest = distance;
                furthestX = bx;
                furthestZ = bz;
            }
        }
        double dx = furthestX - fromBlockX;
        double dz = furthestZ - fromBlockZ;
        double length = Math.hypot(dx, dz);
        if (length < 1) {
            return null;
        }
        int step = FRONTIER_STEP_BLOCKS;
        int targetX = (int) (furthestX + dx / length * step);
        int targetZ = (int) (furthestZ + dz / length * step);

        // Clamp into the bounds so the frontier can never walk outside the authorised area.
        int minBlockX = bounds.minChunkX() << 4;
        int minBlockZ = bounds.minChunkZ() << 4;
        int maxBlockX = (bounds.maxChunkX() << 4) + 15;
        int maxBlockZ = (bounds.maxChunkZ() << 4) + 15;
        targetX = Math.max(minBlockX, Math.min(maxBlockX, targetX));
        targetZ = Math.max(minBlockZ, Math.min(maxBlockZ, targetZ));

        if (wasVisited(targetX, targetZ)) {
            return null;
        }
        return new Cluster(targetX, targetZ, 0, 0, "frontier push outward");
    }

    private static long key(int blockX, int blockZ) {
        return ((long) blockX << 32) ^ (blockZ & 0xFFFFFFFFL);
    }
}
