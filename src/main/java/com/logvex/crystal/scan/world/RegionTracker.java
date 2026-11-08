package com.logvex.crystal.scan.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks which regions of a dimension have been visited, and how completely.
 *
 * <p>Regions are the unit of progress. Every chunk the scanner records marks its region visited;
 * a region is "complete" once all {@link RegionGrid#REGION_CHUNKS}<sup>2</sup> of its chunks have
 * been observed. This is what lets the dashboard say how much of the scan area is done, and what
 * the live map draws as unexplored.
 *
 * <p>The tracker is per-dimension and Minecraft-free, so a dimension change simply switches which
 * index is read and never invalidates the other dimension's progress.
 */
public final class RegionTracker {

    private final Map<String, Map<Long, int[]>> regionsByDimension = new LinkedHashMap<>();

    /** Marks a chunk as observed, creating its region record if needed. */
    public void observe(String dimension, int chunkX, int chunkZ) {
        if (dimension == null) {
            return;
        }
        int regionX = RegionGrid.regionOf(chunkX);
        int regionZ = RegionGrid.regionOf(chunkZ);
        int localX = Math.floorMod(chunkX, RegionGrid.REGION_CHUNKS);
        int localZ = Math.floorMod(chunkZ, RegionGrid.REGION_CHUNKS);
        Map<Long, int[]> regions = regionsByDimension.computeIfAbsent(dimension, d -> new LinkedHashMap<>());
        int[] visited = regions.computeIfAbsent(RegionGrid.key(regionX, regionZ),
                k -> new int[RegionGrid.REGION_CHUNKS * RegionGrid.REGION_CHUNKS]);
        int slot = localZ * RegionGrid.REGION_CHUNKS + localX;
        if (visited[slot] == 0) {
            visited[slot] = 1;
            totalVisited++;
        }
    }

    private int totalVisited;

    /** How many distinct chunks have been observed in a dimension. */
    public int visitedChunks(String dimension) {
        int count = 0;
        for (int[] visited : regions(dimension)) {
            for (int v : visited) {
                count += v;
            }
        }
        return count;
    }

    /** How many regions have at least one observed chunk. */
    public int touchedRegions(String dimension) {
        return regions(dimension).size();
    }

    /** How many regions are fully observed. */
    public int completeRegions(String dimension) {
        int complete = 0;
        for (int[] visited : regions(dimension)) {
            boolean full = true;
            for (int v : visited) {
                if (v == 0) {
                    full = false;
                    break;
                }
            }
            if (full) {
                complete++;
            }
        }
        return complete;
    }

    /** True when every chunk of the region containing {@code (chunkX, chunkZ)} is observed. */
    public boolean regionComplete(String dimension, int chunkX, int chunkZ) {
        Map<Long, int[]> regions = regionsByDimension.get(dimension);
        if (regions == null) {
            return false;
        }
        int[] visited = regions.get(RegionGrid.key(RegionGrid.regionOf(chunkX), RegionGrid.regionOf(chunkZ)));
        if (visited == null) {
            return false;
        }
        for (int v : visited) {
            if (v == 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * The regions inside a chunk-square area that have not been touched at all.
     *
     * @param minChunkX left edge, in chunks
     * @param minChunkZ top edge, in chunks
     * @param maxChunkX right edge, in chunks
     * @param maxChunkZ bottom edge, in chunks
     * @return packed region keys of untouched regions, in row-major order
     */
    public List<Long> unexploredRegions(String dimension, int minChunkX, int minChunkZ,
                                        int maxChunkX, int maxChunkZ) {
        Map<Long, int[]> regions = regionsByDimension.get(dimension);
        List<Long> unexplored = new ArrayList<>();
        int minRX = RegionGrid.regionOf(minChunkX);
        int maxRX = RegionGrid.regionOf(maxChunkX);
        int minRZ = RegionGrid.regionOf(minChunkZ);
        int maxRZ = RegionGrid.regionOf(maxChunkZ);
        for (int rz = minRZ; rz <= maxRZ; rz++) {
            for (int rx = minRX; rx <= maxRX; rx++) {
                long key = RegionGrid.key(rx, rz);
                if (regions == null || !regions.containsKey(key)) {
                    unexplored.add(key);
                }
            }
        }
        return unexplored;
    }

    /** Every region record in a dimension. */
    public Collection<int[]> regions(String dimension) {
        Map<Long, int[]> regions = regionsByDimension.get(dimension);
        return regions == null ? List.of() : regions.values();
    }

    /** The visited-chunk bitmap of one region, or {@code null} when untouched. */
    public int[] regionBitmap(String dimension, int regionX, int regionZ) {
        Map<Long, int[]> regions = regionsByDimension.get(dimension);
        return regions == null ? null : regions.get(RegionGrid.key(regionX, regionZ));
    }

    /** Every dimension the tracker holds progress for. */
    public Collection<String> dimensions() {
        return List.copyOf(regionsByDimension.keySet());
    }

    /** Forgets one dimension's progress. */
    public void clearDimension(String dimension) {
        regionsByDimension.remove(dimension);
    }

    /** Forgets all progress. */
    public void clear() {
        regionsByDimension.clear();
        totalVisited = 0;
    }
}
