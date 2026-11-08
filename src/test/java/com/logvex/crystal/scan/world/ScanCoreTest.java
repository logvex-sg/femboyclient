package com.logvex.crystal.scan.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests the small value types: bounds, config clamping, chunk keys, states and sample helpers. */
class ScanCoreTest {

    @Test
    void boundsRadiusIsSymmetricAndInclusive() {
        ScanBounds bounds = ScanBounds.radius(10, -4, 3);
        assertEquals(7, bounds.minChunkX());
        assertEquals(13, bounds.maxChunkX());
        assertEquals(-7, bounds.minChunkZ());
        assertEquals(-1, bounds.maxChunkZ());
        assertTrue(bounds.contains(10, -4));
        assertTrue(bounds.contains(7, -7));
        assertFalse(bounds.contains(6, -4));
        assertEquals(49, bounds.chunkCount());
    }

    @Test
    void boundsRejectInvertedEdges() {
        assertThrows(IllegalArgumentException.class, () -> new ScanBounds(5, 0, 0, 5));
        assertThrows(IllegalArgumentException.class, () -> new ScanBounds(0, 5, 5, 0));
    }

    @Test
    void configClampsOutOfRangeValues() {
        ScanConfig config = new ScanConfig(-5, 1000, 0, 0, 10000, false, true, 1);
        assertEquals(1, config.radiusChunks());
        assertEquals(128, config.verticalRange());
        assertEquals(1, config.sampleStep());
        assertEquals(1, config.maxChunksPerTick());
        assertEquals(256, config.routeStepBlocks());
        assertEquals(64, config.observationCap());
    }

    @Test
    void chunkKeyRoundTrips() {
        long key = ChunkKey.of(-12345, 67890);
        assertEquals(-12345, ChunkKey.x(key));
        assertEquals(67890, ChunkKey.z(key));
        assertEquals(ChunkKey.of(1, 2), ChunkKey.of(1, 2));
    }

    @Test
    void stateRunningAndTerminalAreCorrect() {
        assertTrue(ScanState.MAPPING.running());
        assertTrue(ScanState.PAUSED.running());
        assertFalse(ScanState.IDLE.running());
        assertTrue(ScanState.COMPLETED.terminal());
        assertTrue(ScanState.ERROR.terminal());
        assertFalse(ScanState.MAPPING.terminal());
        assertEquals("mapping", ScanState.MAPPING.label());
    }

    @Test
    void sampleHelpersComputeRatios() {
        ChunkSample sample = new ChunkSample("d", 0, 0, true, 64, "grass", 100, 50, 40, 10, 5, 10, 10, 0,
                20, 5, 0, 0, 0, 0, 0, TerrainType.PLAINS, 0L);
        assertEquals(0.2, sample.builtRatio(), 1e-9);
        assertEquals(0.25, sample.naturalRatio(), 1e-9);
        assertEquals(0.1, sample.waterRatio(), 1e-9);
        assertFalse(sample.empty());
    }

    @Test
    void unloadedSampleIsMarkedAndNeverEmpty() {
        ChunkSample unloaded = ChunkSample.unloaded("d", 1, 2, 9L);
        assertFalse(unloaded.loaded());
        assertFalse(unloaded.empty());
        assertEquals(0, unloaded.sampled());
        assertEquals(9L, unloaded.observedAtMillis());
    }

    @Test
    void snapshotProgressIsBounded() {
        assertEquals(0.5, ScanSnapshot.progress(5, 10), 1e-9);
        assertEquals(0, ScanSnapshot.progress(0, 0), 1e-9);
        assertEquals(1, ScanSnapshot.progress(20, 10), 1e-9);
    }
}
