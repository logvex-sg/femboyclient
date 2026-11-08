package com.logvex.crystal.scan.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests the observation layer: that a verified observation is only produced for real evidence,
 * that a guess is never presented as a confirmed find, and that the analyzer is deterministic.
 */
class ObservationEngineTest {

    private static ChunkSample sample(int building, int chests, int crops, int spawners, int redstone,
                                      int lights, int portals, int bells, int sampled, long inhabited) {
        return new ChunkSample("minecraft:overworld", 4, -3, true, 64, "stone", sampled, sampled / 2,
                sampled / 2, 0, 0, 0, 0, crops, building, 0, chests, lights, redstone, spawners, portals,
                bells, TerrainType.PLAINS, false, inhabited, true, 100L);
    }

    @Test
    void portalIsSelfEvidentlyConfirmed() {
        ChunkSample s = sample(0, 0, 0, 0, 0, 0, 3, 0, 100, 50L);
        Classification c = StructureClassifier.classify(s, null);
        assertEquals(StructureCategory.PORTAL, c.category());
        assertEquals(VerificationStatus.CONFIRMED, ObservationEngine.verify(s, c));
    }

    @Test
    void lowConfidenceVerdictIsUncertain() {
        // A bare chest with nothing else: the classifier is not confident enough to confirm.
        ChunkSample s = sample(0, 1, 0, 0, 0, 0, 0, 0, 200, 50L);
        Classification c = StructureClassifier.classify(s, null);
        assertEquals(VerificationStatus.UNCERTAIN, ObservationEngine.verify(s, c));
    }

    @Test
    void strongStorageWithManySignalsIsConfirmed() {
        ChunkSample s = sample(40, 6, 0, 0, 0, 5, 0, 0, 100, 500L);
        Classification c = StructureClassifier.classify(s, null);
        assertEquals(StructureCategory.STORAGE, c.category());
        assertTrue(c.confidence() >= ObservationEngine.SELF_EVIDENT_CONFIDENCE);
        assertEquals(VerificationStatus.CONFIRMED, ObservationEngine.verify(s, c));
    }

    @Test
    void unloadedSampleIsAlwaysUncertain() {
        ChunkSample unloaded = ChunkSample.unloaded("d", 0, 0, 1L);
        Classification c = new Classification(StructureCategory.BUILDING, 0.99, java.util.List.of("x"));
        assertEquals(VerificationStatus.UNCERTAIN, ObservationEngine.verify(unloaded, c));
    }

    @Test
    void summaryNeverClaimsCertaintyForAGuess() {
        ChunkSample s = sample(0, 1, 0, 0, 0, 0, 0, 0, 200, 50L);
        Classification c = StructureClassifier.classify(s, null);
        String summary = ObservationEngine.summary(s, c, VerificationStatus.UNCERTAIN);
        assertTrue(summary.contains("possible"));
        assertTrue(summary.contains("unconfirmed"));
    }

    @Test
    void evidenceCombinesReasonsAndSignals() {
        ChunkSample s = sample(40, 6, 0, 0, 0, 5, 0, 0, 100, 500L);
        Classification c = StructureClassifier.classify(s, null);
        var evidence = ObservationEngine.evidence(s, c);
        assertFalse(evidence.isEmpty());
        assertTrue(evidence.stream().anyMatch(e -> e.contains("container")));
    }

    @Test
    void analyzerIsDeterministicAndAssignsIds() {
        ChunkAnalyzer analyzer = new ChunkAnalyzer();
        ChunkSample s = sample(40, 6, 0, 0, 0, 5, 0, 0, 100, 500L);
        Observation first = analyzer.analyze("sess", s, 1000L);
        Observation second = analyzer.analyze("sess", s, 1000L);
        assertEquals(first.category(), second.category());
        assertEquals(first.verification(), second.verification());
        assertFalse(first.id().equals(second.id()));
        assertEquals(2, analyzer.count());
        assertEquals("minecraft:overworld", first.dimension());
        assertTrue(first.discovered());
        assertEquals((4 << 4) + 8, first.blockX());
    }

    @Test
    void analyzerSkipsUnloadedSamples() {
        ChunkAnalyzer analyzer = new ChunkAnalyzer();
        assertNull(analyzer.analyze("s", ChunkSample.unloaded("d", 0, 0, 1L), 1L));
    }
}
