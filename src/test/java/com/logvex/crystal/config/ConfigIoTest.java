package com.logvex.crystal.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies the shared config IO: tolerant reads, atomic writes and the backup copy. */
class ConfigIoTest {

    @Test
    void readsAbsentFileAsNull(@TempDir Path dir) {
        assertNull(ConfigIo.readObject(dir.resolve("missing.json")));
    }

    @Test
    void readsMalformedFileAsNull(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("broken.json");
        Files.writeString(file, "{ not json", StandardCharsets.UTF_8);
        assertNull(ConfigIo.readObject(file));
    }

    @Test
    void readsNonObjectAsNull(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("array.json");
        Files.writeString(file, "[1,2,3]", StandardCharsets.UTF_8);
        assertNull(ConfigIo.readObject(file));
    }

    @Test
    void malformedFileNotifiesTheRecoveryListener(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("broken.json");
        Files.writeString(file, "{ not json", StandardCharsets.UTF_8);
        java.util.List<String> recovered = new java.util.ArrayList<>();
        ConfigIo.onRecovery(recovered::add);
        try {
            assertNull(ConfigIo.readObject(file));
        } finally {
            ConfigIo.onRecovery(null);
        }
        assertEquals(java.util.List.of("broken.json"), recovered);
    }

    @Test
    void absentFileDoesNotNotifyRecovery(@TempDir Path dir) {
        java.util.List<String> recovered = new java.util.ArrayList<>();
        ConfigIo.onRecovery(recovered::add);
        try {
            assertNull(ConfigIo.readObject(dir.resolve("missing.json")));
        } finally {
            ConfigIo.onRecovery(null);
        }
        assertTrue(recovered.isEmpty());
    }

    @Test
    void healthyFileDoesNotNotifyRecovery(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("data.json");
        ConfigIo.writeObject(file, new JsonObject());
        java.util.List<String> recovered = new java.util.ArrayList<>();
        ConfigIo.onRecovery(recovered::add);
        try {
            assertNotNull(ConfigIo.readObject(file));
        } finally {
            ConfigIo.onRecovery(null);
        }
        assertTrue(recovered.isEmpty());
    }

    @Test
    void writeThenReadRoundTrips(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("data.json");
        JsonObject root = new JsonObject();
        root.addProperty("value", 42);
        ConfigIo.writeObject(file, root);

        JsonObject read = ConfigIo.readObject(file);
        assertNotNull(read);
        assertEquals(42, read.get("value").getAsInt());
    }

    @Test
    void writeLeavesNoTempFileBehind(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("data.json");
        ConfigIo.writeObject(file, new JsonObject());
        assertFalse(Files.exists(dir.resolve("data.json.tmp")));
    }

    @Test
    void secondWriteKeepsBackupOfTheFirst(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("data.json");
        JsonObject first = new JsonObject();
        first.addProperty("generation", 1);
        ConfigIo.writeObject(file, first);

        JsonObject second = new JsonObject();
        second.addProperty("generation", 2);
        ConfigIo.writeObject(file, second);

        assertTrue(Files.exists(ConfigIo.backupPath(file)), "backup should exist after an overwrite");
        assertEquals(1, ConfigIo.readObject(ConfigIo.backupPath(file)).get("generation").getAsInt());
        assertEquals(2, ConfigIo.readObject(file).get("generation").getAsInt());
    }

    @Test
    void firstWriteHasNoBackup(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("fresh.json");
        ConfigIo.writeObject(file, new JsonObject());
        assertFalse(Files.exists(ConfigIo.backupPath(file)), "nothing to back up on the first write");
    }

    @Test
    void writeCreatesMissingParentDirectories(@TempDir Path dir) throws IOException {
        Path nested = dir.resolve("a/b/c/data.json");
        ConfigIo.writeObject(nested, new JsonObject());
        assertTrue(Files.exists(nested));
    }

    @Test
    void isCorruptDistinguishesMissingMalformedAndHealthy(@TempDir Path dir) throws IOException {
        Path missing = dir.resolve("missing.json");
        assertFalse(ConfigIo.isCorrupt(missing), "a missing file is not corrupt");

        Path healthy = dir.resolve("ok.json");
        JsonObject ok = new JsonObject();
        ok.addProperty("v", 1);
        ConfigIo.writeObject(healthy, ok);
        assertFalse(ConfigIo.isCorrupt(healthy));

        Path broken = dir.resolve("broken.json");
        Files.writeString(broken, "{ not json", StandardCharsets.UTF_8);
        assertTrue(ConfigIo.isCorrupt(broken));
    }

    @Test
    void corruptFileIsRestoredFromBackupOnRead(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("data.json");
        JsonObject first = new JsonObject();
        first.addProperty("generation", 1);
        ConfigIo.writeObject(file, first);
        JsonObject second = new JsonObject();
        second.addProperty("generation", 2);
        ConfigIo.writeObject(file, second);

        Files.writeString(file, "{ corrupted", StandardCharsets.UTF_8);

        java.util.List<String> restores = new java.util.ArrayList<>();
        ConfigIo.onRestore(restores::add);
        JsonObject read = ConfigIo.readObject(file);
        ConfigIo.onRestore(null);
        assertNotNull(read, "the backup should have been restored and read");
        assertEquals(1, read.get("generation").getAsInt());
        assertEquals(java.util.List.of("data.json"), restores);
    }

    @Test
    void missingFileIsRestoredFromBackup(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("data.json");
        JsonObject first = new JsonObject();
        first.addProperty("generation", 1);
        ConfigIo.writeObject(file, first);
        JsonObject second = new JsonObject();
        second.addProperty("generation", 2);
        ConfigIo.writeObject(file, second);

        Files.delete(file);
        JsonObject read = ConfigIo.readObject(file);
        assertNotNull(read);
        assertEquals(1, read.get("generation").getAsInt());
    }

    @Test
    void healthyFileIsNeverOverwrittenByBackup(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("data.json");
        JsonObject first = new JsonObject();
        first.addProperty("generation", 1);
        ConfigIo.writeObject(file, first);
        JsonObject second = new JsonObject();
        second.addProperty("generation", 2);
        ConfigIo.writeObject(file, second);

        JsonObject read = ConfigIo.readObject(file);
        assertEquals(2, read.get("generation").getAsInt(), "the live file wins over the backup");
    }

    @Test
    void corruptBackupIsNotUsed(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("data.json");
        JsonObject first = new JsonObject();
        first.addProperty("generation", 1);
        ConfigIo.writeObject(file, first);
        JsonObject second = new JsonObject();
        second.addProperty("generation", 2);
        ConfigIo.writeObject(file, second);

        Files.writeString(file, "{ corrupted", StandardCharsets.UTF_8);
        Files.writeString(ConfigIo.backupPath(file), "{ also corrupted", StandardCharsets.UTF_8);

        assertNull(ConfigIo.readObject(file), "a corrupt backup must not be trusted");
    }
}
