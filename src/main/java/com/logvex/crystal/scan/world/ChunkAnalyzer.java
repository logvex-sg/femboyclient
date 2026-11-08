package com.logvex.crystal.scan.world;

import java.util.concurrent.atomic.AtomicLong;

/**
 * The analysis step: turns a raw {@link ChunkSample} into a verified {@link Observation}.
 *
 * <p>It composes the deterministic {@link StructureClassifier} (what is it?) with the
 * {@link ObservationEngine} (how sure are we, and why?) and stamps the result with an id and a
 * session. It holds no Minecraft types and no I/O, so it runs identically on the client thread or
 * on the classifier worker, and is directly unit testable.
 *
 * <p>An optional {@code hint} category (for example from a local model) is passed straight through
 * to the classifier, which only consults it when the deterministic evidence is inconclusive. The
 * hint can never turn a weak sample into a confirmed one: the verification status is derived from
 * the block evidence, not from the hint.
 */
public final class ChunkAnalyzer {

    private final AtomicLong sequence = new AtomicLong();

    /** Analyses a sample with no external hint. */
    public Observation analyze(String sessionId, ChunkSample sample, long nowMillis) {
        return analyze(sessionId, sample, (StructureCategory) null, nowMillis);
    }

    /** Analyses a sample, optionally biased by an external {@code hint} category, and returns the
     * verified observation. Returns {@code null} for a sample that carries nothing worth recording. */
    public Observation analyze(String sessionId, ChunkSample sample, StructureCategory hint, long nowMillis) {
        if (sample == null || !sample.loaded()) {
            return null;
        }
        return analyze(sessionId, sample, StructureClassifier.classify(sample, hint), nowMillis);
    }

    /**
     * Analyses a sample using a classification that has already been computed (for example by the
     * scanner's off-thread worker), so the classification is never done twice for one chunk.
     */
    public Observation analyze(String sessionId, ChunkSample sample, Classification classification,
                               long nowMillis) {
        if (sample == null || !sample.loaded() || classification == null) {
            return null;
        }
        VerificationStatus status = ObservationEngine.verify(sample, classification);
        String id = sessionId + ":" + sample.chunkX() + ":" + sample.chunkZ() + ":"
                + sequence.incrementAndGet();
        return new Observation(id, sessionId, sample.dimension(), sample.chunkX(), sample.chunkZ(),
                nowMillis, classification.category(), classification.confidence(), status,
                ObservationEngine.evidence(sample, classification), sample.signals(), "");
    }

    /** How many observations this analyzer has produced. */
    public long count() {
        return sequence.get();
    }

    /** Resets the id sequence (used when re-importing a session so ids stay compact). */
    public void reset() {
        sequence.set(0);
    }
}
