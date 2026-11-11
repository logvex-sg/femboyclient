package com.logvex.crystal.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Organisation, bookmarks and persistence for waypoints. */
class WaypointRegistryTest {

    @Test
    void unsetMetadataReportsTheDefaultCategory(@TempDir Path dir) {
        WaypointRegistry registry = new WaypointRegistry(dir.resolve("meta.json"));
        assertEquals(WaypointRegistry.DEFAULT_CATEGORY, registry.metaOf("base").category());
    }

    @Test
    void metadataRoundTripsThroughJson(@TempDir Path dir) {
        WaypointRegistry registry = new WaypointRegistry(dir.resolve("meta.json"));
        registry.setMeta("base", "Home", "Overworld", "my main stash");
        registry.putBookmark(new WaypointRegistry.Bookmark("mine", 10, 64, -20, "minecraft:overworld"));

        WaypointRegistry reloaded = new WaypointRegistry(dir.resolve("meta.json"));
        reloaded.apply(registry.toJson());
        assertEquals("home", reloaded.metaOf("BASE").category());
        assertEquals("overworld", reloaded.metaOf("base").group());
        assertEquals("my main stash", reloaded.metaOf("base").notes());
        assertEquals(1, reloaded.bookmarks().size());
        assertEquals(-20, reloaded.bookmark("mine").z());
    }

    @Test
    void renamingFollowsTheOrganisationToTheNewName(@TempDir Path dir) {
        WaypointRegistry registry = new WaypointRegistry(dir.resolve("meta.json"));
        registry.setMeta("old", "stash", "nether", "");
        registry.rename("old", "new");
        assertEquals("stash", registry.metaOf("new").category());
        assertEquals(WaypointRegistry.DEFAULT_CATEGORY, registry.metaOf("old").category());
    }

    @Test
    void categoriesAndGroupsListEveryValueInUse(@TempDir Path dir) {
        WaypointRegistry registry = new WaypointRegistry(dir.resolve("meta.json"));
        registry.setMeta("a", "stash", "nether", "");
        registry.setMeta("b", "home", "overworld", "");
        assertTrue(registry.categories().containsAll(List.of("general", "stash", "home")));
        assertTrue(registry.groups().containsAll(List.of("nether", "overworld")));
    }

    @Test
    void aNewerSchemaIsRefusedRatherThanDowngraded(@TempDir Path dir) {
        WaypointRegistry registry = new WaypointRegistry(dir.resolve("meta.json"));
        registry.setMeta("base", "home", "", "");
        JsonObject future = registry.toJson();
        future.addProperty("schema", WaypointRegistry.SCHEMA_VERSION + 1);

        WaypointRegistry target = new WaypointRegistry(dir.resolve("meta.json"));
        target.apply(future);
        assertEquals(WaypointRegistry.DEFAULT_CATEGORY, target.metaOf("base").category());
    }

    @Test
    void malformedEntriesAreSkippedNotFatal(@TempDir Path dir) {
        WaypointRegistry registry = new WaypointRegistry(dir.resolve("meta.json"));
        JsonObject root = new JsonObject();
        root.addProperty("schema", WaypointRegistry.SCHEMA_VERSION);
        var metadata = new com.google.gson.JsonArray();
        metadata.add("not an object");
        JsonObject good = new JsonObject();
        good.addProperty("name", "base");
        good.addProperty("category", "stash");
        metadata.add(good);
        root.add("metadata", metadata);

        registry.apply(root);
        assertEquals("stash", registry.metaOf("base").category());
    }

    @Test
    void bookmarksCanBeAddedAndRemoved(@TempDir Path dir) {
        WaypointRegistry registry = new WaypointRegistry(dir.resolve("meta.json"));
        registry.putBookmark(new WaypointRegistry.Bookmark("a", 1, 2, 3, ""));
        assertNotNull(registry.bookmark("A"));
        assertTrue(registry.removeBookmark("a"));
        assertNull(registry.bookmark("a"));
    }
}
