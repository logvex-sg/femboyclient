package com.logvex.crystal.scan.world;

/**
 * Groups chunks into square regions so progress can be described and rendered at a coarser
 * granularity than individual chunks.
 *
 * <p>A region is {@link #REGION_CHUNKS} chunks on a side (16 by default, one Minecraft
 * "region" file's worth). A scanned area is usually described in regions, not chunks: "explored
 * 4 of 9 regions" is far more useful than "explored 1024 of 2304 chunks", and a live map can draw
 * one cell per region instead of thousands.
 *
 * <p>Minecraft-free integer maths only, so it is trivially testable and safe to call from a tick.
 */
public final class RegionGrid {

    /** Chunks per region side. */
    public static final int REGION_CHUNKS = 16;
    /** Blocks per region side. */
    public static final int REGION_BLOCKS = REGION_CHUNKS << 4;

    private RegionGrid() {
    }

    /** The region a chunk coordinate falls in (floor division, correct for negatives). */
    public static int regionOf(int chunkCoord) {
        return Math.floorDiv(chunkCoord, REGION_CHUNKS);
    }

    /** The first chunk coordinate of a region. */
    public static int firstChunkOf(int regionCoord) {
        return regionCoord * REGION_CHUNKS;
    }

    /** The region's centre in block coordinates. */
    public static int centreBlockOf(int regionCoord) {
        return firstChunkOf(regionCoord) * 16 + (REGION_BLOCKS / 2);
    }

    /** A packed key for a region, matching {@link ChunkKey}'s packing. */
    public static long key(int regionX, int regionZ) {
        return ChunkKey.of(regionX, regionZ);
    }

    /** The region x of a packed region key. */
    public static int keyX(long key) {
        return ChunkKey.x(key);
    }

    /** The region z of a packed region key. */
    public static int keyZ(long key) {
        return ChunkKey.z(key);
    }

    /** The number of regions that fit across a span of {@code chunkSpan} chunks (rounded up). */
    public static int regionCount(int chunkSpan) {
        return Math.max(1, (chunkSpan + REGION_CHUNKS - 1) / REGION_CHUNKS);
    }
}
