package com.logvex.crystal.scan.world;

/**
 * Packs a chunk coordinate pair into a single {@code long} for use as a map key, so the world
 * mapper can index chunks without allocating a key object per lookup.
 */
public final class ChunkKey {

    private ChunkKey() {
    }

    /** Packs {@code (chunkX, chunkZ)} into a long (x in the high 32 bits). */
    public static long of(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public static int x(long key) {
        return (int) (key >> 32);
    }

    public static int z(long key) {
        return (int) key;
    }
}
