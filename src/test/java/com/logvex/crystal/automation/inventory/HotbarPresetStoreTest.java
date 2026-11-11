package com.logvex.crystal.automation.inventory;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

/** Covers hotbar preset add/replace/remove and JSON round-tripping, including corruption. */
class HotbarPresetStoreTest {

    @Test
    void putReplacesByNameCaseInsensitively() {
        HotbarPresetStore store = new HotbarPresetStore(Path.of("unused.json"));
        store.put("PvP", new String[]{"minecraft:totem", null, null, null, null, null, null, null, null});
        store.put("pvp", new String[]{"minecraft:obsidian", null, null, null, null, null, null, null, null});
        assertEquals(1, store.size());
        assertEquals("minecraft:obsidian", store.byName("PVP").slots()[0]);
    }

    @Test
    void slotsArrayIsAlwaysHotbarSizedAndCopied() {
        HotbarPresetStore store = new HotbarPresetStore(Path.of("unused.json"));
        store.put("x", new String[]{"a"});
        String[] slots = store.byName("x").slots();
        assertEquals(HotbarPresetStore.HOTBAR_SIZE, slots.length);
        slots[0] = "mutated";
        assertEquals("a", store.byName("x").slots()[0]);
    }

    @Test
    void removeIsCaseInsensitive() {
        HotbarPresetStore store = new HotbarPresetStore(Path.of("unused.json"));
        store.put("Build", new String[HotbarPresetStore.HOTBAR_SIZE]);
        assertTrue(store.remove("build"));
        assertFalse(store.remove("build"));
        assertNull(store.byName("Build"));
    }

    @Test
    void savesAndLoadsThroughDisk(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("hotbar-presets.json");
        HotbarPresetStore store = new HotbarPresetStore(file);
        String[] slots = new String[HotbarPresetStore.HOTBAR_SIZE];
        slots[0] = "minecraft:totem";
        slots[8] = "minecraft:obsidian";
        store.put("Anarchy", slots);
        store.save();
        assertTrue(Files.exists(file));

        HotbarPresetStore loaded = new HotbarPresetStore(file);
        loaded.load();
        assertEquals(1, loaded.size());
        assertArrayEquals(slots, loaded.byName("Anarchy").slots());
    }

    @Test
    void refusesAFutureSchemaRatherThanDowngrading(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("hotbar-presets.json");
        JsonObject root = new JsonObject();
        root.addProperty("schema", HotbarPresetStore.SCHEMA_VERSION + 1);
        com.logvex.crystal.config.ConfigIo.writeObject(file, root);
        HotbarPresetStore store = new HotbarPresetStore(file);
        store.load();
        assertEquals(0, store.size());
    }

    @Test
    void skipsMalformedEntriesOnLoad(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("hotbar-presets.json");
        JsonObject root = new JsonObject();
        root.addProperty("schema", HotbarPresetStore.SCHEMA_VERSION);
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        JsonObject good = new JsonObject();
        good.addProperty("name", "good");
        com.google.gson.JsonArray slots = new com.google.gson.JsonArray();
        slots.add("minecraft:totem");
        good.add("slots", slots);
        array.add(good);
        array.add(new com.google.gson.JsonPrimitive("not-an-object"));
        JsonObject missingSlots = new JsonObject();
        missingSlots.addProperty("name", "bad");
        array.add(missingSlots);
        root.add("presets", array);
        com.logvex.crystal.config.ConfigIo.writeObject(file, root);

        HotbarPresetStore store = new HotbarPresetStore(file);
        store.load();
        assertEquals(1, store.size());
        assertNotNull(store.byName("good"));
    }
}
