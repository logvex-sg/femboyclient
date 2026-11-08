package com.logvex.crystal.scan.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests the discovery database: dedupe, search/filter/sort, annotations, deletion and persistence. */
class DiscoveryDatabaseTest {

    private static Observation observation(int chunkX, int chunkZ, StructureCategory category,
                                           VerificationStatus status, long at, String note) {
        ObservationSignals signals = new ObservationSignals(0.3, 2, 0, 0, 0, 1, 0, 0, false, 100L, true);
        return new Observation("id-" + chunkX + "-" + chunkZ, "sess", "minecraft:overworld", chunkX, chunkZ,
                at, category, 0.8, status, List.of("evidence"), signals, note);
    }

    @Test
    void addDeduplicatesByChunk() {
        DiscoveryDatabase db = new DiscoveryDatabase(Path.of("unused"));
        assertTrue(db.add(observation(1, 1, StructureCategory.STORAGE, VerificationStatus.CONFIRMED, 10L, "")));
        assertFalse(db.add(observation(1, 1, StructureCategory.FARM, VerificationStatus.LIKELY, 20L, "")));
        assertEquals(1, db.size());
        assertTrue(db.contains(1, 1));
        assertEquals(StructureCategory.FARM, db.observations().get(0).category());
    }

    @Test
    void annotationsAndVerificationSurvive() {
        DiscoveryDatabase db = new DiscoveryDatabase(Path.of("unused"));
        db.add(observation(2, 2, StructureCategory.BUILDING, VerificationStatus.UNCERTAIN, 10L, ""));
        Observation annotated = db.annotate("id-2-2", "my base");
        assertNotNull(annotated);
        assertEquals("my base", annotated.note());
        Observation rejected = db.setVerification("id-2-2", VerificationStatus.REJECTED);
        assertEquals(VerificationStatus.REJECTED, rejected.verification());
        assertFalse(rejected.discovered());
    }

    @Test
    void searchMatchesCategoryNoteAndEvidence() {
        DiscoveryDatabase db = new DiscoveryDatabase(Path.of("unused"));
        db.add(observation(1, 1, StructureCategory.STORAGE, VerificationStatus.CONFIRMED, 10L, ""));
        db.add(observation(2, 2, StructureCategory.VILLAGE, VerificationStatus.LIKELY, 20L, "bell town"));
        assertEquals(1, db.search("village", null, DiscoveryDatabase.SortOrder.RECENT, 0, 10).size());
        assertEquals(1, db.search("bell", null, DiscoveryDatabase.SortOrder.RECENT, 0, 10).size());
        assertEquals(2, db.search("", null, DiscoveryDatabase.SortOrder.RECENT, 0, 10).size());
    }

    @Test
    void searchFiltersAndPaginates() {
        DiscoveryDatabase db = new DiscoveryDatabase(Path.of("unused"));
        for (int i = 0; i < 10; i++) {
            db.add(observation(i, 0, i % 2 == 0 ? StructureCategory.STORAGE : StructureCategory.FARM,
                    VerificationStatus.CONFIRMED, i, ""));
        }
        var storages = db.search("", o -> o.category() == StructureCategory.STORAGE,
                DiscoveryDatabase.SortOrder.CATEGORY, 0, 100);
        assertEquals(5, storages.size());
        var page = db.search("", null, DiscoveryDatabase.SortOrder.OLDEST, 2, 3);
        assertEquals(3, page.size());
        assertEquals(2L, page.get(0).atMillis());
    }

    @Test
    void deletionsWork() {
        DiscoveryDatabase db = new DiscoveryDatabase(Path.of("unused"));
        db.add(observation(1, 1, StructureCategory.STORAGE, VerificationStatus.CONFIRMED, 10L, ""));
        db.add(observation(2, 2, StructureCategory.STORAGE, VerificationStatus.CONFIRMED, 10L, ""));
        assertTrue(db.delete("id-1-1"));
        assertEquals(1, db.size());
        assertEquals(1, db.deleteDimension("minecraft:overworld"));
        assertEquals(0, db.size());
    }

    @Test
    void countsAndDimensions() {
        DiscoveryDatabase db = new DiscoveryDatabase(Path.of("unused"));
        db.add(observation(1, 1, StructureCategory.STORAGE, VerificationStatus.CONFIRMED, 10L, ""));
        db.add(observation(2, 2, StructureCategory.FARM, VerificationStatus.UNCERTAIN, 10L, ""));
        assertEquals(1, db.discoveries("minecraft:overworld").size());
        assertEquals(1, db.categoryCounts("minecraft:overworld").get(StructureCategory.STORAGE));
        assertEquals(2, db.countSince(0));
        assertEquals(1, db.dimensions().size());
    }

    @Test
    void roundTripsThroughDisk(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("world-intelligence.json");
        DiscoveryDatabase db = new DiscoveryDatabase(file);
        db.add(observation(3, -4, StructureCategory.VILLAGE, VerificationStatus.LIKELY, 55L, "notes"));
        db.addRoute(new DiscoveryDatabase.RouteEntry("minecraft:overworld", 100, 200, 60L, "reached"));
        db.addSummary(new DiscoveryDatabase.AiSummary("session", "minecraft:overworld", 70L, "summary text",
                false));
        db.save();

        DiscoveryDatabase reloaded = new DiscoveryDatabase(file);
        reloaded.load();
        assertEquals(1, reloaded.size());
        Observation restored = reloaded.observations().get(0);
        assertEquals(StructureCategory.VILLAGE, restored.category());
        assertEquals(VerificationStatus.LIKELY, restored.verification());
        assertEquals("notes", restored.note());
        assertNotNull(restored.signals());
        assertEquals(2, restored.signals().storageCount());
        assertEquals(1, reloaded.routeHistory().size());
        assertEquals(1, reloaded.summaries().size());
    }

    @Test
    void corruptFileLoadsAsEmpty(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("broken.json");
        Files.writeString(file, "{ this is not json ");
        DiscoveryDatabase db = new DiscoveryDatabase(file);
        db.load();
        assertEquals(0, db.size());
    }

    @Test
    void newerSchemaIsRefusedNotDowngraded(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("future.json");
        Files.writeString(file, "{\"schema\": 999, \"observations\": [{\"id\":\"x\",\"dimension\":\"d\"}]}");
        DiscoveryDatabase db = new DiscoveryDatabase(file);
        db.load();
        assertEquals(0, db.size());
    }

    @Test
    void malformedObservationsAreSkipped(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("partial.json");
        Files.writeString(file, "{\"schema\":1,\"observations\":[{\"id\":\"ok\",\"dimension\":\"d\"},{},5]}");
        DiscoveryDatabase db = new DiscoveryDatabase(file);
        db.load();
        assertEquals(1, db.size());
        assertEquals(2, db.skipped());
    }

    @Test
    void parseHelpersAreTolerant() {
        assertEquals(StructureCategory.OTHER, DiscoveryDatabase.parseCategory("nonsense"));
        assertEquals(StructureCategory.OTHER, DiscoveryDatabase.parseCategory(null));
        assertEquals(StructureCategory.STORAGE, DiscoveryDatabase.parseCategory("storage"));
        assertEquals(VerificationStatus.UNCERTAIN, DiscoveryDatabase.parseVerification("nonsense"));
        assertEquals(VerificationStatus.CONFIRMED, DiscoveryDatabase.parseVerification("confirmed"));
    }
}
