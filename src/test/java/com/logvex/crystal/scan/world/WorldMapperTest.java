package com.logvex.crystal.scan.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests the world map: indexing, dedupe, manual corrections, discovery counting and eviction. */
class WorldMapperTest {

    private static ChunkSample sample(String dimension, int chunkX, int chunkZ, int building) {
        return new ChunkSample(dimension, chunkX, chunkZ, true, 64, "stone", 100, 100, 100, 0, 0, 0, 0, 0,
                building, 0, 0, 0, 0, 0, 0, TerrainType.PLAINS, 0L);
    }

    private static Classification storage() {
        return new Classification(StructureCategory.STORAGE, 0.8, java.util.List.of("chests"));
    }

    @Test
    void recordsAndIndexesByChunk() {
        WorldMapper mapper = new WorldMapper(1000);
        mapper.record(sample("overworld", 3, 4, 0), storage(), 1L);
        assertTrue(mapper.contains("overworld", 3, 4));
        assertEquals(1, mapper.size("overworld"));
        assertSame(StructureCategory.STORAGE, mapper.get("overworld", 3, 4).category());
    }

    @Test
    void dimensionsAreIsolated() {
        WorldMapper mapper = new WorldMapper(1000);
        mapper.record(sample("overworld", 0, 0, 0), storage(), 1L);
        mapper.record(sample("the_nether", 0, 0, 0), storage(), 1L);
        assertEquals(1, mapper.size("overworld"));
        assertEquals(1, mapper.size("the_nether"));
        assertEquals(2, mapper.totalSize());
        assertFalse(mapper.chunks("overworld").isEmpty());
    }

    @Test
    void reobservingAChunkUpdatesRatherThanDuplicates() {
        WorldMapper mapper = new WorldMapper(1000);
        mapper.record(sample("overworld", 1, 1, 0), storage(), 1L);
        mapper.record(sample("overworld", 1, 1, 50), storage(), 2L);
        assertEquals(1, mapper.size("overworld"));
        assertEquals(50, mapper.get("overworld", 1, 1).sample().building());
        assertEquals(2L, mapper.get("overworld", 1, 1).updatedAtMillis());
    }

    @Test
    void manualCorrectionWinsOverAutomaticReclassification() {
        WorldMapper mapper = new WorldMapper(1000);
        mapper.record(sample("overworld", 2, 2, 0), storage(), 1L);
        mapper.correct("overworld", 2, 2, StructureCategory.FARM, 2L);
        mapper.record(sample("overworld", 2, 2, 60), storage(), 3L);
        MappedChunk chunk = mapper.get("overworld", 2, 2);
        assertTrue(chunk.manual());
        assertSame(StructureCategory.FARM, chunk.category());
    }

    @Test
    void discoveryCountOnlyCountsConfidentNonOther() {
        WorldMapper mapper = new WorldMapper(1000);
        mapper.record(sample("overworld", 0, 0, 0), storage(), 1L);
        mapper.record(sample("overworld", 1, 0, 0), Classification.unknown("weak"), 1L);
        assertEquals(1, mapper.discoveryCount("overworld"));
    }

    @Test
    void recentIsNewestFirst() {
        WorldMapper mapper = new WorldMapper(1000);
        mapper.record(sample("overworld", 0, 0, 0), storage(), 10L);
        mapper.record(sample("overworld", 1, 0, 0), storage(), 20L);
        assertEquals(1, mapper.recent("overworld", 1).get(0).sample().chunkX());
    }

    @Test
    void evictsOldestWhenCapReached() {
        WorldMapper mapper = new WorldMapper(64);
        for (int i = 0; i < 100; i++) {
            mapper.record(sample("overworld", i, 0, 0), storage(), i);
        }
        assertEquals(64, mapper.size("overworld"));
        assertFalse(mapper.contains("overworld", 0, 0));
        assertTrue(mapper.contains("overworld", 99, 0));
    }

    @Test
    void versionAdvancesOnEveryMutation() {
        WorldMapper mapper = new WorldMapper(1000);
        long start = mapper.version();
        mapper.record(sample("overworld", 0, 0, 0), storage(), 1L);
        assertTrue(mapper.version() > start);
        mapper.clear();
        assertTrue(mapper.version() > start);
        assertEquals(0, mapper.totalSize());
    }

    @Test
    void nullAndUnknownDimensionAreTolerated() {
        WorldMapper mapper = new WorldMapper(1000);
        assertNull(mapper.record(null, storage(), 1L));
        assertEquals(0, mapper.size("nowhere"));
        assertNull(mapper.get("nowhere", 0, 0));
    }
}
