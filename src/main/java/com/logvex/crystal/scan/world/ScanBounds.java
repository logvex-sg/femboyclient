package com.logvex.crystal.scan.world;

/**
 * The region a scan is allowed to cover, in chunk coordinates.
 *
 * <p>Bounds are inclusive on every edge. They exist so an authorised scan stays inside the
 * area the user asked for, and so the scanner can detect when it has reached a world edge or
 * a limit and stop rather than wandering. A {@link #radius} constructor builds a square of
 * chunks around a centre; {@link #contains} is the single place the "inside the scan area"
 * rule lives.
 */
public record ScanBounds(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {

    public ScanBounds {
        if (maxChunkX < minChunkX) {
            throw new IllegalArgumentException("maxChunkX < minChunkX");
        }
        if (maxChunkZ < minChunkZ) {
            throw new IllegalArgumentException("maxChunkZ < minChunkZ");
        }
    }

    /** A square of chunks of half-width {@code radius} around a centre chunk. */
    public static ScanBounds radius(int centerChunkX, int centerChunkZ, int radius) {
        int r = Math.max(0, radius);
        return new ScanBounds(centerChunkX - r, centerChunkZ - r, centerChunkX + r, centerChunkZ + r);
    }

    public boolean contains(int chunkX, int chunkZ) {
        return chunkX >= minChunkX && chunkX <= maxChunkX && chunkZ >= minChunkZ && chunkZ <= maxChunkZ;
    }

    /** Total number of chunks in the region. */
    public int chunkCount() {
        return (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
    }

    public int width() {
        return maxChunkX - minChunkX + 1;
    }

    public int depth() {
        return maxChunkZ - minChunkZ + 1;
    }

    /** Centre chunk (rounded toward the minimum for even spans). */
    public int centerChunkX() {
        return minChunkX + width() / 2;
    }

    public int centerChunkZ() {
        return minChunkZ + depth() / 2;
    }
}
