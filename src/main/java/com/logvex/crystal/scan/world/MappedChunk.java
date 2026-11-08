package com.logvex.crystal.scan.world;

/**
 * One entry in the world map: the sampled chunk plus the classifier's current verdict.
 *
 * <p>{@code manual} marks a verdict the user corrected by hand; the mapper never overwrites a
 * manual classification, so a user's judgement always wins over the automatic one.
 *
 * <p>{@code verification} records how well-evidenced the verdict is, so a view can show a
 * confirmed find differently from an uncertain guess rather than treating every label alike.
 */
public record MappedChunk(
        ChunkSample sample,
        StructureCategory category,
        double confidence,
        VerificationStatus verification,
        boolean manual,
        long updatedAtMillis) {

    public MappedChunk {
        if (verification == null) {
            verification = confidence >= 0.6 && category != StructureCategory.OTHER
                    ? VerificationStatus.LIKELY : VerificationStatus.UNCERTAIN;
        }
    }

    /** Convenience constructor that derives the verification status from the confidence. */
    public MappedChunk(ChunkSample sample, StructureCategory category, double confidence, boolean manual,
                       long updatedAtMillis) {
        this(sample, category, confidence,
                confidence >= 0.6 && category != StructureCategory.OTHER
                        ? VerificationStatus.LIKELY : VerificationStatus.UNCERTAIN,
                manual, updatedAtMillis);
    }

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
        return withClassification(classification, VerificationStatus.LIKELY, nowMillis);
    }

    /** A copy with a new automatic classification and an explicit verification status. */
    public MappedChunk withClassification(Classification classification, VerificationStatus status,
                                           long nowMillis) {
        if (manual) {
            return this;
        }
        VerificationStatus resolved = status == null ? VerificationStatus.LIKELY : status;
        return new MappedChunk(sample, classification.category(), classification.confidence(), resolved, false,
                nowMillis);
    }

    /** A copy with a user correction; a corrected entry is always treated as confirmed. */
    public MappedChunk withManual(StructureCategory corrected, long nowMillis) {
        return new MappedChunk(sample, corrected, 1.0, VerificationStatus.CONFIRMED, true, nowMillis);
    }

    /** True when this entry may be counted as a real discovery. */
    public boolean discovered() {
        return manual || verification.countsAsDiscovery();
    }
}
