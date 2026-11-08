package com.logvex.crystal.scan.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests the deterministic natural-language query engine: it answers from data and admits ignorance. */
class WorldQueryTest {

    private static DiscoveryDatabase seeded() {
        DiscoveryDatabase db = new DiscoveryDatabase(java.nio.file.Path.of("unused"));
        db.add(new Observation("a", "s", "minecraft:overworld", 0, 0, 10L, StructureCategory.VILLAGE,
                0.7, VerificationStatus.LIKELY, List.of("bell"), null, ""));
        db.add(new Observation("b", "s", "minecraft:overworld", 5, 5, 10L, StructureCategory.STORAGE,
                0.9, VerificationStatus.CONFIRMED, List.of("chests"), null, ""));
        db.add(new Observation("c", "s", "minecraft:overworld", 9, 9, 10L, StructureCategory.FARM,
                0.3, VerificationStatus.UNCERTAIN, List.of("crops"), null, ""));
        return db;
    }

    private static WorldQuery.Context context(DiscoveryDatabase db, RegionTracker regions) {
        return new WorldQuery.Context(db, regions, "minecraft:overworld", new ScanBounds(0, 0, 31, 31));
    }

    @Test
    void answersCountQuestion() {
        WorldQuery query = new WorldQuery();
        var answer = query.ask("how many discoveries are recorded", context(seeded(), new RegionTracker()));
        assertTrue(answer.supported());
        assertTrue(answer.text().contains("3 observation"));
        assertTrue(answer.text().contains("2 of them discoveries"));
    }

    @Test
    void findsCategoryObservations() {
        WorldQuery query = new WorldQuery();
        var answer = query.ask("find all recorded villages", context(seeded(), new RegionTracker()));
        assertTrue(answer.supported());
        assertEquals("category:VILLAGE", answer.intent());
        assertEquals(1, answer.observationIds().size());
        assertTrue(answer.text().contains("village"));
    }

    @Test
    void reportsUncertainObservationsAsGuesses() {
        WorldQuery query = new WorldQuery();
        var answer = query.ask("show uncertain observations", context(seeded(), new RegionTracker()));
        assertTrue(answer.supported());
        assertTrue(answer.text().contains("guesses, not finds"));
        assertEquals(1, answer.observationIds().size());
    }

    @Test
    void reportsUnexploredRegions() {
        RegionTracker regions = new RegionTracker();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                regions.observe("minecraft:overworld", x, z);
            }
        }
        WorldQuery query = new WorldQuery();
        var answer = query.ask("show unexplored regions", context(seeded(), regions));
        assertTrue(answer.supported());
        assertTrue(answer.text().contains("unexplored"));
    }

    @Test
    void summarisesTodaysExploration() {
        WorldQuery query = new WorldQuery();
        var answer = query.ask("summarize today's exploration", context(seeded(), new RegionTracker()));
        assertTrue(answer.supported());
        assertTrue(answer.text().contains("Total in the database: 3"));
    }

    @Test
    void unknownQuestionIsUnsupportedAndOffersHelp() {
        WorldQuery query = new WorldQuery();
        var answer = query.ask("what is the meaning of life", context(seeded(), new RegionTracker()));
        assertFalse(answer.supported());
        assertTrue(answer.text().contains("I do not understand"));
        assertTrue(answer.text().contains("show unexplored regions"));
    }

    @Test
    void exportIntentIsRecognised() {
        WorldQuery query = new WorldQuery();
        var answer = query.ask("export my current world map", context(seeded(), new RegionTracker()));
        assertTrue(answer.supported());
        assertEquals("export", answer.intent());
    }

    @Test
    void emptyQuestionIsUnsupported() {
        WorldQuery query = new WorldQuery();
        assertFalse(query.ask("   ", context(seeded(), new RegionTracker())).supported());
        assertFalse(query.ask(null, context(seeded(), new RegionTracker())).supported());
    }

    @Test
    void shortDimensionStripsNamespace() {
        assertEquals("overworld", WorldQuery.shortDimension("minecraft:overworld"));
        assertEquals("nether", WorldQuery.shortDimension("nether"));
        assertEquals("unknown", WorldQuery.shortDimension(null));
    }
}
