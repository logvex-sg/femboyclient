package com.logvex.crystal.scan.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests scan persistence: round-tripping, tolerating corruption, history capping and import. */
class ScanDatabaseTest {

    private static ChunkSample sample(int chunkX, int chunkZ) {
        return new ChunkSample("minecraft:overworld", chunkX, chunkZ, true, 64, "stone", 100, 100, 100, 0, 0,
                0, 0, 0, 50, 0, 2, 0, 0, 0, 0, TerrainType.PLAINS, 10L);
    }

    private static MappedChunk mapped(int chunkX, int chunkZ, StructureCategory category, double confidence) {
        return new MappedChunk(sample(chunkX, chunkZ), category, confidence, false, 10L);
    }

    @Test
    void sessionsRoundTripThroughDisk(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("scan.json");
        ScanDatabase database = new ScanDatabase(file);
        ScanSession session = database.begin("minecraft:overworld", new ScanBounds(0, 0, 4, 4), 100L);
        session.recordChunk(mapped(1, 1, StructureCategory.STORAGE, 0.9), 101L);
        session.recordChunk(mapped(2, 2, StructureCategory.NATURAL, 0.6), 102L);
        session.setState(ScanState.COMPLETED, 103L);
        database.end(ScanState.COMPLETED, 103L);
        database.save();

        ScanDatabase reloaded = new ScanDatabase(file);
        reloaded.load();
        assertEquals(1, reloaded.sessions().size());
        ScanSession restored = reloaded.sessions().get(0);
        assertEquals("minecraft:overworld", restored.dimension());
        assertEquals(2, restored.chunkCount());
        assertEquals(ScanState.COMPLETED, restored.state());
        assertEquals(StructureCategory.STORAGE, restored.chunk(1, 1).category());
    }

    @Test
    void duplicateChunksAreNotStoredTwice(@TempDir Path dir) {
        ScanDatabase database = new ScanDatabase(dir.resolve("scan.json"));
        ScanSession session = database.begin("d", new ScanBounds(0, 0, 4, 4), 1L);
        assertTrue(session.recordChunk(mapped(0, 0, StructureCategory.FARM, 0.7), 2L));
        assertFalse(session.recordChunk(mapped(0, 0, StructureCategory.FARM, 0.7), 3L));
        assertEquals(1, session.chunkCount());
        assertEquals(1, session.duplicates());
    }

    @Test
    void manualCorrectionSurvivesReload(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("scan.json");
        ScanDatabase database = new ScanDatabase(file);
        ScanSession session = database.begin("d", new ScanBounds(0, 0, 4, 4), 1L);
        session.recordChunk(mapped(3, 3, StructureCategory.OTHER, 0.3), 2L);
        session.correct(3, 3, StructureCategory.MINE, 3L);
        database.save();

        ScanDatabase reloaded = new ScanDatabase(file);
        reloaded.load();
        ScanSession.ChunkRecord record = reloaded.sessions().get(0).chunk(3, 3);
        assertTrue(record.manual());
        assertEquals(StructureCategory.MINE, record.category());
    }

    @Test
    void missingFileLoadsEmpty(@TempDir Path dir) {
        ScanDatabase database = new ScanDatabase(dir.resolve("nope.json"));
        database.load();
        assertTrue(database.sessions().isEmpty());
        assertEquals(0, database.skipped());
    }

    @Test
    void corruptFileLoadsEmptyRatherThanThrowing(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("scan.json");
        Files.writeString(file, "{ this is not json ");
        ScanDatabase database = new ScanDatabase(file);
        database.load();
        assertTrue(database.sessions().isEmpty());
    }

    @Test
    void malformedSessionEntriesAreSkipped(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("scan.json");
        // A well-formed document with one good session and one missing its id.
        JsonObject root = new JsonObject();
        root.addProperty("schema", ScanDatabase.SCHEMA_VERSION);
        var sessions = new com.google.gson.JsonArray();
        ScanDatabase db = new ScanDatabase(dir.resolve("other.json"));
        ScanSession good = db.begin("d", new ScanBounds(0, 0, 1, 1), 1L);
        sessions.add(ScanDatabase.sessionToJson(good));
        sessions.add(new JsonObject());
        root.add("sessions", sessions);
        Files.writeString(file, root.toString());

        ScanDatabase reloaded = new ScanDatabase(file);
        reloaded.load();
        assertEquals(1, reloaded.sessions().size());
        assertEquals(1, reloaded.skipped());
    }

    @Test
    void futureSchemaIsIgnored(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("scan.json");
        JsonObject root = new JsonObject();
        root.addProperty("schema", ScanDatabase.SCHEMA_VERSION + 5);
        Files.writeString(file, root.toString());
        ScanDatabase database = new ScanDatabase(file);
        database.load();
        assertTrue(database.sessions().isEmpty());
    }

    @Test
    void historyIsCapped(@TempDir Path dir) {
        ScanDatabase database = new ScanDatabase(dir.resolve("scan.json"));
        for (int i = 0; i < ScanDatabase.HISTORY_CAP + 10; i++) {
            database.begin("d", new ScanBounds(0, 0, 1, 1), i);
            database.end(ScanState.COMPLETED, i);
        }
        assertEquals(ScanDatabase.HISTORY_CAP, database.sessions().size());
    }

    @Test
    void profilesRoundTripAndDelete(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("scan.json");
        ScanDatabase database = new ScanDatabase(file);
        database.putProfile(new ScanProfile("fast", new ScanConfig(4, 16, 8, 16, 64, true, true, 1000)));
        database.save();

        ScanDatabase reloaded = new ScanDatabase(file);
        reloaded.load();
        ScanProfile profile = reloaded.profile("FAST");
        assertNotNull(profile);
        assertEquals(4, profile.config().radiusChunks());
        assertEquals(16, profile.config().maxChunksPerTick());
        assertTrue(reloaded.deleteProfile("fast"));
        assertNull(reloaded.profile("fast"));
    }

    @Test
    void exportThenImportReproducesTheSession(@TempDir Path dir) {
        ScanDatabase database = new ScanDatabase(dir.resolve("scan.json"));
        ScanSession session = database.begin("minecraft:the_end", new ScanBounds(0, 0, 3, 3), 50L);
        session.recordChunk(mapped(1, 2, StructureCategory.BUILDING, 0.75), 51L);
        JsonObject document = ScanDatabase.export(session);

        ScanSession imported = ScanDatabase.importSession(document);
        assertNotNull(imported);
        assertEquals("minecraft:the_end", imported.dimension());
        assertEquals(1, imported.chunkCount());
        assertEquals(StructureCategory.BUILDING, imported.chunk(1, 2).category());
    }

    @Test
    void importRejectsGarbage() {
        assertNull(ScanDatabase.importSession(null));
        assertNull(ScanDatabase.importSession(new JsonObject()));
    }

    @Test
    void deletingASessionRemovesIt(@TempDir Path dir) {
        ScanDatabase database = new ScanDatabase(dir.resolve("scan.json"));
        ScanSession session = database.begin("d", new ScanBounds(0, 0, 1, 1), 1L);
        String id = session.id();
        database.end(ScanState.COMPLETED, 2L);
        assertNotNull(database.byId(id));
        assertTrue(database.delete(id));
        assertNull(database.byId(id));
        assertFalse(database.delete(id));
    }
}
