package com.logvex.crystal.scan.world;

/**
 * One entry in the world map: the sampled chunk plus the classifier's current verdict.
 *
 * <p>{@code manual} marks a verdict the user corrected by hand; the mapper never overwrites a
 * manual classification, so a user's judgement always wins over the automatic one.
 */
public record MappedChunk(
        ChunkSample sample,
        StructureCategory category,
        double confidence,
        boolean manual,
        long updatedAtMillis) {

    /** The chunk's stable map key. */
    public long key() {
        return ChunkKey.of(sample.chunkX(), sample.chunkZ());
    }

    /** True when this entry is a confirmed (loaded, classified) observation. */
    public boolean observed() {
        return sample.loaded();
    }

    /** A copy with a new automatic classification, preserving manual verdicts. */
    public MappedChunk withClassification(Classification classification, long nowMillis) {
        if (manual) {
            return this;
        }
        return new MappedChunk(sample, classification.category(), classification.confidence(), false, nowMillis);
    }

    /** A copy with a user correction. */
    public MappedChunk withManual(StructureCategory corrected, long nowMillis) {
        return new MappedChunk(sample, corrected, 1.0, true, nowMillis);
    }
}
