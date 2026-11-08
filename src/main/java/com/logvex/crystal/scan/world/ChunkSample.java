package com.logvex.crystal.scan.world;

/**
 * A Minecraft-free summary of one chunk the scanner sampled.
 *
 * <p>The Minecraft adapter walks a bounded grid of columns inside the chunk the client already
 * has loaded and counts the block families the classifier cares about. Everything downstream
 * (classification, mapping, persistence) works from these counts, so none of it needs a live
 * world and all of it is unit testable.
 *
 * <p>{@code loaded == false} means the chunk was not loaded when sampled: it is recorded so
 * the scanner remembers to revisit it later, but it carries no counts and is never classified.
 *
 * @param dimension       the dimension id, e.g. {@code minecraft:overworld}
 * @param chunkX          chunk x
 * @param chunkZ          chunk z
 * @param loaded          whether the chunk was loaded at sample time
 * @param surfaceY        the highest non-air y found in the sampled columns, or {@code MIN_VALUE}
 * @param surfaceBlock    the block id of the surface column, or {@code ""} when unknown
 * @param sampled         number of positions sampled
 * @param nonAir          sampled positions that were not air
 * @param solid           sampled positions that were opaque solid blocks
 * @param transparent     sampled positions that were see-through (glass, leaves, water, ...)
 * @param water           sampled positions that were a fluid
 * @param logs            log/stem blocks
 * @param leaves          leaf blocks
 * @param crops           crop-like blocks (wheat, carrots, ...)
 * @param building        man-made building blocks (planks, bricks, concrete, ...)
 * @param ores            ore blocks
 * @param chests          chest/trapped-chest/shulker/barrel block entities or blocks
 * @param lightSources    light-emitting blocks (torches, lanterns, glowstone, ...)
 * @param redstone        redstone components (dust, repeater, piston, ...)
 * @param spawners        spawner blocks
 * @param portals         portal blocks (nether/end)
 * @param bells           village marker blocks (bells, and jigsaw/structure blocks)
 * @param terrain         the coarse terrain classification
 * @param flowingLiquids  true when flowing (not source) liquid was seen in the chunk
 * @param inhabitedTicks  the chunk's inhabited time in ticks, or {@code -1} when the client does
 *                        not expose it
 * @param lightComplete   whether the server sent a populated light layer, or {@code null} when
 *                        the client cannot tell
 * @param observedAtMillis wall-clock time the sample was taken
 */
public record ChunkSample(
        String dimension,
        int chunkX,
        int chunkZ,
        boolean loaded,
        int surfaceY,
        String surfaceBlock,
        int sampled,
        int nonAir,
        int solid,
        int transparent,
        int water,
        int logs,
        int leaves,
        int crops,
        int building,
        int ores,
        int chests,
        int lightSources,
        int redstone,
        int spawners,
        int portals,
        int bells,
        TerrainType terrain,
        boolean flowingLiquids,
        long inhabitedTicks,
        Boolean lightComplete,
        long observedAtMillis) {

    /** A sample for a chunk that was not loaded: no counts, nothing to classify. */
    public static ChunkSample unloaded(String dimension, int chunkX, int chunkZ, long nowMillis) {
        return new ChunkSample(dimension, chunkX, chunkZ, false, Integer.MIN_VALUE, "", 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, TerrainType.UNKNOWN, false, -1L, null, nowMillis);
    }

    /**
     * Convenience constructor for the common case where the extra intelligence signals (village
     * markers, flowing liquids, inhabited time, light completeness) are not known. Keeps callers
     * that only have the basic block counts simple, and defaults the unknowns to "not observed".
     */
    public ChunkSample(String dimension, int chunkX, int chunkZ, boolean loaded, int surfaceY,
                       String surfaceBlock, int sampled, int nonAir, int solid, int transparent, int water,
                       int logs, int leaves, int crops, int building, int ores, int chests, int lightSources,
                       int redstone, int spawners, int portals, TerrainType terrain, long observedAtMillis) {
        this(dimension, chunkX, chunkZ, loaded, surfaceY, surfaceBlock, sampled, nonAir, solid, transparent,
                water, logs, leaves, crops, building, ores, chests, lightSources, redstone, spawners, portals,
                0, terrain, false, -1L, null, observedAtMillis);
    }

    /**
     * The independent evidence signals behind this sample, for {@link VerificationStatus}.
     *
     * <p>Only raw counts are stored on the record; the derived signals are computed here so the
     * stored form stays small and a change to the thresholds cannot corrupt persisted data.
     */
    public ObservationSignals signals() {
        return new ObservationSignals(builtRatio(), chests, spawners, crops, redstone, lightSources,
                ores, logs + leaves, flowingLiquids, inhabitedTicks, lightComplete);
    }

    /** True when the chunk looked like void or an empty/unloaded column with no blocks at all. */
    public boolean empty() {
        return loaded && sampled > 0 && nonAir == 0;
    }

    /** Fraction of sampled positions that were man-made building blocks. */
    public double builtRatio() {
        return sampled == 0 ? 0 : building / (double) sampled;
    }

    /** Fraction of sampled positions that were natural (non-air, non-built) blocks. */
    public double naturalRatio() {
        if (sampled == 0) {
            return 0;
        }
        int natural = logs + leaves + crops + ores;
        return natural / (double) sampled;
    }

    /** Fraction of the sampled non-air positions that were fluid. */
    public double waterRatio() {
        return nonAir == 0 ? 0 : water / (double) nonAir;
    }

    /** The chunk's centre x in block coordinates. */
    public int centerBlockX() {
        return (chunkX << 4) + 8;
    }

    /** The chunk's centre z in block coordinates. */
    public int centerBlockZ() {
        return (chunkZ << 4) + 8;
    }
}
