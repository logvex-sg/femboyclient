package com.logvex.crystal.scan.world;

/**
 * The narrow port the scanner uses to observe the world. The Minecraft adapter implements it;
 * a fake implements it in tests.
 *
 * <p>Every method reads state the client already has. The contract forbids forcing chunk loads:
 * {@link #chunkLoaded} must reflect the client's own loaded set, and {@link #sample} must never
 * pull a chunk the client does not already have. That is the single guarantee that keeps the
 * scanner from bypassing server-side protections.
 */
public interface WorldSampler {

    /** The current dimension id, e.g. {@code minecraft:overworld}. */
    String dimension();

    /** The player's block x. */
    int playerBlockX();

    /** The player's block y. */
    int playerBlockY();

    /** The player's block z. */
    int playerBlockZ();

    /** True when the client already has this chunk loaded. Must never force a load. */
    boolean chunkLoaded(int chunkX, int chunkZ);

    /** A bounded sample of the chunk, or {@link ChunkSample#unloaded} when it is not loaded. */
    ChunkSample sample(int chunkX, int chunkZ, ScanConfig config, long nowMillis);

    /** True when the world is currently loaded and the player exists. */
    boolean worldLoaded();

    /** True when the player exists and is alive. */
    boolean playerAlive();
}
