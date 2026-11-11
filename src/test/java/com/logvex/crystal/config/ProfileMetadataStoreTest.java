package com.logvex.crystal.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Profile sidecar metadata: descriptions, categories, default and context. */
class ProfileMetadataStoreTest {

    @Test
    void unsetMetadataReportsTheDefaultCategory(@TempDir Path dir) {
        ProfileMetadataStore store = new ProfileMetadataStore(dir.resolve("meta.json"));
        assertEquals(ProfileMetadataStore.DEFAULT_CATEGORY, store.category("anything"));
        assertEquals("", store.description("anything"));
    }

    @Test
    void metadataRoundTripsThroughJson(@TempDir Path dir) {
        ProfileMetadataStore store = new ProfileMetadataStore(dir.resolve("meta.json"));
        store.of("base").description = "my base profile";
        store.of("base").category = "survival";
        store.of("base").context = "mc.example.com";
        store.of("base").tags.add("daily");
        store.setDefault("base");
        store.save();

        ProfileMetadataStore reloaded = new ProfileMetadataStore(dir.resolve("meta.json"));
        assertEquals("my base profile", reloaded.description("base"));
        assertEquals("survival", reloaded.category("base"));
        assertEquals("mc.example.com", reloaded.peek("base").context);
        assertTrue(reloaded.peek("base").tags.contains("daily"));
        assertEquals("base", reloaded.defaultProfile());
        assertTrue(reloaded.peek("base").isDefault);
    }

    @Test
    void onlyOneProfileCanBeTheDefault(@TempDir Path dir) {
        ProfileMetadataStore store = new ProfileMetadataStore(dir.resolve("meta.json"));
        store.setDefault("a");
        store.setDefault("b");
        assertFalse(store.peek("a").isDefault);
        assertTrue(store.peek("b").isDefault);
        assertEquals("b", store.defaultProfile());
    }

    @Test
    void renameFollowsTheMetadata(@TempDir Path dir) {
        ProfileMetadataStore store = new ProfileMetadataStore(dir.resolve("meta.json"));
        store.of("old").description = "kept";
        store.setDefault("old");
        store.rename("old", "new");
        assertEquals("kept", store.description("new"));
        assertEquals("new", store.defaultProfile());
    }

    @Test
    void removeDropsTheEntryAndClearsTheDefault(@TempDir Path dir) {
        ProfileMetadataStore store = new ProfileMetadataStore(dir.resolve("meta.json"));
        store.of("base").category = "survival";
        store.setDefault("base");
        store.remove("base");
        assertEquals(ProfileMetadataStore.DEFAULT_CATEGORY, store.category("base"));
        assertEquals("", store.defaultProfile());
    }

    @Test
    void contextMatchesAServerAddress(@TempDir Path dir) {
        ProfileMetadataStore store = new ProfileMetadataStore(dir.resolve("meta.json"));
        store.of("anarchy").context = "2b2t.org";
        store.of("other").context = "hypixel.net";
        var matches = store.forContext("2b2t.org");
        assertEquals(1, matches.size());
        assertTrue(matches.contains("anarchy"));
    }

    @Test
    void aNewerSchemaIsRefused(@TempDir Path dir) {
        ProfileMetadataStore store = new ProfileMetadataStore(dir.resolve("meta.json"));
        store.of("base").category = "survival";
        JsonObject future = store.toJson();
        future.addProperty("schema", ProfileMetadataStore.SCHEMA_VERSION + 1);

        ProfileMetadataStore target = new ProfileMetadataStore(dir.resolve("meta.json"));
        target.apply(future);
        assertEquals(ProfileMetadataStore.DEFAULT_CATEGORY, target.category("base"));
    }

    @Test
    void pruneDropsMetadataForDeletedProfiles(@TempDir Path dir) {
        ProfileMetadataStore store = new ProfileMetadataStore(dir.resolve("meta.json"));
        store.of("keep").category = "survival";
        store.of("gone").category = "combat";
        store.prune(java.util.List.of("keep"));
        assertEquals("survival", store.category("keep"));
        assertEquals(ProfileMetadataStore.DEFAULT_CATEGORY, store.category("gone"));
    }
}
