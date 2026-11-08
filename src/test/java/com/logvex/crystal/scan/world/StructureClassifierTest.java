package com.logvex.crystal.scan.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests the deterministic classifier: every category is reachable from block evidence, a weak
 * verdict is downgraded rather than asserted, a hint only breaks a tie, and malformed input is
 * handled instead of throwing.
 */
class StructureClassifierTest {

    private static ChunkSample sample(int sampled, int nonAir, int building, int crops, int chests,
                                      int redstone, int ores, int logs, int leaves, int water, int spawners,
                                      int portals, int surfaceY, TerrainType terrain) {
        return new ChunkSample("minecraft:overworld", 0, 0, true, surfaceY, "grass_block", sampled, nonAir,
                nonAir, 0, water, logs, leaves, crops, building, ores, chests, 0, redstone, spawners, portals,
                terrain, 0L);
    }

    @Test
    void storageFromChests() {
        Classification c = StructureClassifier.classify(sample(100, 40, 30, 0, 3, 0, 0, 0, 0, 0, 0, 0, 64,
                TerrainType.PLAINS));
        assertEquals(StructureCategory.STORAGE, c.category());
        assertTrue(c.confidence() >= 0.6);
        assertTrue(c.confident());
    }

    @Test
    void farmFromCrops() {
        Classification c = StructureClassifier.classify(sample(100, 50, 5, 20, 0, 0, 0, 0, 0, 0, 0, 0, 64,
                TerrainType.PLAINS));
        assertEquals(StructureCategory.FARM, c.category());
    }

    @Test
    void buildingFromBuiltDensity() {
        Classification c = StructureClassifier.classify(sample(100, 60, 40, 0, 0, 0, 0, 0, 0, 0, 0, 0, 64,
                TerrainType.PLAINS));
        assertEquals(StructureCategory.BUILDING, c.category());
    }

    @Test
    void portalIsUnambiguous() {
        Classification c = StructureClassifier.classify(sample(100, 10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 64,
                TerrainType.PLAINS));
        assertEquals(StructureCategory.PORTAL, c.category());
    }

    @Test
    void spawnerIsMobFarm() {
        Classification c = StructureClassifier.classify(sample(100, 20, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 30,
                TerrainType.CAVE));
        assertEquals(StructureCategory.MOB_FARM, c.category());
    }

    @Test
    void redstoneFromComponents() {
        Classification c = StructureClassifier.classify(sample(100, 30, 2, 0, 0, 8, 0, 0, 0, 0, 0, 0, 64,
                TerrainType.PLAINS));
        assertEquals(StructureCategory.REDSTONE, c.category());
    }

    @Test
    void mineFromDeepOres() {
        Classification c = StructureClassifier.classify(sample(100, 80, 1, 0, 0, 0, 6, 0, 0, 0, 0, 0, 30,
                TerrainType.CAVE));
        assertEquals(StructureCategory.MINE, c.category());
    }

    @Test
    void naturalFromVegetation() {
        Classification c = StructureClassifier.classify(sample(100, 60, 0, 0, 0, 0, 0, 10, 20, 0, 0, 0, 70,
                TerrainType.FOREST));
        assertEquals(StructureCategory.NATURAL, c.category());
    }

    @Test
    void weakEvidenceIsOtherNotAConfidentGuess() {
        Classification c = StructureClassifier.classify(sample(100, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 64,
                TerrainType.UNKNOWN));
        assertEquals(StructureCategory.OTHER, c.category());
        assertFalse(c.confident());
    }

    @Test
    void hintBreaksATieButStaysLowConfidence() {
        ChunkSample weak = sample(100, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 64, TerrainType.UNKNOWN);
        Classification withHint = StructureClassifier.classify(weak, StructureCategory.FARM);
        assertEquals(StructureCategory.FARM, withHint.category());
        assertTrue(withHint.confidence() <= 0.55, "a hint must not create a confident claim");
        assertFalse(withHint.confident());
    }

    @Test
    void hintIsIgnoredWhenEvidenceIsStrong() {
        ChunkSample strong = sample(100, 40, 30, 0, 3, 0, 0, 0, 0, 0, 0, 0, 64, TerrainType.PLAINS);
        Classification c = StructureClassifier.classify(strong, StructureCategory.PORTAL);
        assertEquals(StructureCategory.STORAGE, c.category());
    }

    @Test
    void nullAndUnloadedSamplesAreHandled() {
        assertEquals(StructureCategory.OTHER, StructureClassifier.classify(null).category());
        ChunkSample unloaded = ChunkSample.unloaded("minecraft:overworld", 0, 0, 0L);
        assertEquals(StructureCategory.OTHER, StructureClassifier.classify(unloaded).category());
        assertFalse(StructureClassifier.classify(unloaded).confident());
    }

    @Test
    void terrainClassificationRecognisesCommonSurfaces() {
        assertEquals(TerrainType.NETHER, StructureClassifier.classifyTerrain("netherrack", 0, 10, 0, 0, false));
        assertEquals(TerrainType.END, StructureClassifier.classifyTerrain("end_stone", 0, 10, 0, 0, false));
        assertEquals(TerrainType.DESERT, StructureClassifier.classifyTerrain("sand", 0, 10, 0, 0, false));
        assertEquals(TerrainType.SNOW, StructureClassifier.classifyTerrain("snow_block", 0, 10, 0, 0, false));
        assertEquals(TerrainType.PLAINS, StructureClassifier.classifyTerrain("grass_block", 0, 10, 0, 0, false));
        assertEquals(TerrainType.FOREST, StructureClassifier.classifyTerrain("grass_block", 0, 10, 4, 8, false));
    }

    @Test
    void classificationSummaryIsHumanReadable() {
        Classification c = new Classification(StructureCategory.STORAGE, 0.8, List.of("chests"));
        assertEquals("storage 80%", c.summary());
    }
}
