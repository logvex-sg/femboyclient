package com.logvex.crystal.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContainerLabelsTest {

    @Test
    void keyCombinesDimensionAndPosition() {
        assertEquals("minecraft:overworld@10,64,-5",
                ContainerLabels.key("minecraft:overworld", 10, 64, -5));
        assertEquals("unknown@0,0,0", ContainerLabels.key(null, 0, 0, 0));
        assertEquals("unknown@1,2,3", ContainerLabels.key("  ", 1, 2, 3));
    }

    @Test
    void setGetRemoveAndBlankClears() {
        ContainerLabels labels = new ContainerLabels(Path.of("unused"));
        String key = ContainerLabels.key("minecraft:overworld", 1, 2, 3);
        labels.set(key, "  Base Storage  ");
        assertEquals("Base Storage", labels.get(key));
        labels.set(key, "   ");
        assertEquals("", labels.get(key));
        assertEquals(0, labels.size());
    }

    @Test
    void searchIsCaseInsensitive() {
        ContainerLabels labels = new ContainerLabels(Path.of("unused"));
        labels.set(ContainerLabels.key("minecraft:overworld", 1, 2, 3), "Base Storage");
        labels.set(ContainerLabels.key("minecraft:the_nether", 9, 9, 9), "Nether Loot");
        assertEquals(2, labels.search("").size());
        assertEquals(1, labels.search("loot").size());
        assertEquals(1, labels.search("BASE").size());
        assertTrue(labels.search("nothing").isEmpty());
    }

    @Test
    void roundTripsThroughJson(@TempDir Path dir) {
        ContainerLabels source = new ContainerLabels(dir.resolve("labels.json"));
        source.set(ContainerLabels.key("minecraft:overworld", 4, 5, 6), "Farm");
        JsonObject json = source.toJson();

        ContainerLabels loaded = new ContainerLabels(dir.resolve("labels.json"));
        loaded.apply(json);
        assertEquals("Farm", loaded.get(ContainerLabels.key("minecraft:overworld", 4, 5, 6)));
        assertEquals(1, loaded.size());
    }

    @Test
    void newerSchemaIsIgnoredAndMalformedIsSafe() {
        ContainerLabels labels = new ContainerLabels(Path.of("unused"));
        labels.set(ContainerLabels.key("minecraft:overworld", 1, 1, 1), "Old");
        JsonObject future = new JsonObject();
        future.addProperty("schema", ContainerLabels.SCHEMA_VERSION + 1);
        JsonObject map = new JsonObject();
        map.addProperty("minecraft:overworld@1,1,1", "New");
        future.add("labels", map);
        labels.apply(future);
        assertTrue(labels.all().isEmpty(), "a newer schema must not be applied");

        JsonObject malformed = new JsonObject();
        malformed.addProperty("labels", "not-an-object");
        labels.apply(malformed);
        assertTrue(labels.all().isEmpty());
    }

    @Test
    void allReturnsACopy() {
        ContainerLabels labels = new ContainerLabels(Path.of("unused"));
        labels.set(ContainerLabels.key("minecraft:overworld", 1, 1, 1), "A");
        Map<String, String> copy = labels.all();
        copy.clear();
        assertEquals(1, labels.size(), "mutating the returned map must not touch the store");
    }

    @Test
    void removeAndClear() {
        ContainerLabels labels = new ContainerLabels(Path.of("unused"));
        String key = ContainerLabels.key("minecraft:overworld", 1, 1, 1);
        labels.set(key, "A");
        labels.remove(key);
        assertFalse(labels.all().containsKey(key));
        labels.set(key, "A");
        labels.clear();
        assertEquals(0, labels.size());
    }
}
