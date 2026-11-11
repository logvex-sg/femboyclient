package com.logvex.crystal.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The bounded, persisted profile change log. */
class ProfileHistoryTest {

    @Test
    void recordsChangesNewestFirst(@TempDir Path dir) {
        ProfileHistory history = new ProfileHistory(dir.resolve("history.json"));
        history.record(ProfileHistory.Action.SAVED, "a", "");
        history.record(ProfileHistory.Action.LOADED, "b", "");
        var recent = history.recent();
        assertEquals(2, recent.size());
        assertEquals(ProfileHistory.Action.LOADED, recent.get(0).action());
        assertEquals("b", recent.get(0).profile());
    }

    @Test
    void survivesAReload(@TempDir Path dir) {
        Path file = dir.resolve("history.json");
        ProfileHistory history = new ProfileHistory(file);
        history.record(ProfileHistory.Action.RENAMED, "new", "from old");

        ProfileHistory reloaded = new ProfileHistory(file);
        assertEquals(1, reloaded.recent().size());
        assertEquals(ProfileHistory.Action.RENAMED, reloaded.recent().get(0).action());
        assertEquals("from old", reloaded.recent().get(0).detail());
    }

    @Test
    void isBounded(@TempDir Path dir) {
        ProfileHistory history = new ProfileHistory(dir.resolve("history.json"));
        for (int i = 0; i < 250; i++) {
            history.record(ProfileHistory.Action.SAVED, "p" + i, "");
        }
        assertTrue(history.recent().size() <= 100);
    }

    @Test
    void clearEmptiesTheLog(@TempDir Path dir) {
        ProfileHistory history = new ProfileHistory(dir.resolve("history.json"));
        history.record(ProfileHistory.Action.SAVED, "a", "");
        history.clear();
        assertTrue(history.recent().isEmpty());
    }

    @Test
    void aMalformedEntryIsSkipped(@TempDir Path dir) {
        ProfileHistory history = new ProfileHistory(dir.resolve("history.json"));
        history.record(ProfileHistory.Action.SAVED, "a", "");
        var json = history.toJson();
        var entries = json.getAsJsonArray("entries");
        var bad = new com.google.gson.JsonObject();
        bad.addProperty("action", "NOT_AN_ACTION");
        entries.add(bad);

        ProfileHistory target = new ProfileHistory(dir.resolve("history.json"));
        target.apply(json);
        assertEquals(1, target.recent().size());
    }
}
