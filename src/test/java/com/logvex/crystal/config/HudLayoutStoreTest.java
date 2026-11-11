package com.logvex.crystal.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.logvex.crystal.gui.HudLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Round-trip and resilience tests for the HUD layout codec. These run without a Minecraft
 * runtime because the codec only touches {@link HudLayout}, which is a plain data holder.
 */
class HudLayoutStoreTest {

    @Test
    void roundTripsEveryField(@TempDir Path dir) throws IOException {
        HudLayout layout = new HudLayout();
        HudLayout.Entry entry = layout.entry("watermark");
        entry.visible = false;
        entry.customPosition = true;
        entry.x = 42;
        entry.y = -7;
        entry.opacity = 0.35f;
        entry.scale = 1.6f;

        HudLayoutStore store = new HudLayoutStore(dir.resolve("hud.json"));
        store.save(layout);

        HudLayout loaded = new HudLayout();
        store.loadInto(loaded);
        HudLayout.Entry result = loaded.entry("watermark");
        assertFalse(result.visible);
        assertTrue(result.customPosition);
        assertEquals(42, result.x);
        assertEquals(-7, result.y);
        assertEquals(0.35f, result.opacity, 1e-6);
        assertEquals(1.6f, result.scale, 1e-6);
    }

    @Test
    void absentFileLeavesLayoutUntouched(@TempDir Path dir) {
        HudLayout layout = new HudLayout();
        HudLayout.Entry entry = layout.entry("coords");
        entry.x = 11;
        new HudLayoutStore(dir.resolve("missing.json")).loadInto(layout);
        assertEquals(11, layout.entry("coords").x);
    }

    @Test
    void corruptFileIsIgnored(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("hud.json");
        Files.writeString(file, "not json at all");
        HudLayout layout = new HudLayout();
        new HudLayoutStore(file).loadInto(layout);
        // Defaults remain: a fresh entry is visible and auto-stacked.
        assertTrue(layout.entry("watermark").visible);
        assertFalse(layout.entry("watermark").customPosition);
    }

    @Test
    void newerSchemaIsRefused() {
        HudLayout layout = new HudLayout();
        JsonObject root = HudLayoutStore.toJson(layout);
        root.addProperty("schema", HudLayoutStore.SCHEMA_VERSION + 1);
        JsonObject elements = new JsonObject();
        JsonObject entry = new JsonObject();
        entry.addProperty("x", 99);
        elements.add("watermark", entry);
        root.add("elements", elements);
        HudLayoutStore.apply(root, layout);
        assertEquals(0, layout.entry("watermark").x);
    }

    @Test
    void outOfRangeValuesAreClamped() {
        HudLayout layout = new HudLayout();
        JsonObject root = new JsonObject();
        root.addProperty("schema", HudLayoutStore.SCHEMA_VERSION);
        JsonObject elements = new JsonObject();
        JsonObject entry = new JsonObject();
        entry.addProperty("opacity", 5.0);
        entry.addProperty("scale", 0.01);
        elements.add("module-list", entry);
        root.add("elements", elements);
        HudLayoutStore.apply(root, layout);
        assertEquals(1.0f, layout.entry("module-list").opacity, 1e-6);
        assertEquals(0.5f, layout.entry("module-list").scale, 1e-6);
    }

    @Test
    void backgroundAndBorderRoundTrip() {
        HudLayout layout = new HudLayout();
        layout.entry("watermark").background = true;
        layout.entry("watermark").border = true;
        JsonObject json = HudLayoutStore.toJson(layout);
        HudLayout restored = new HudLayout();
        HudLayoutStore.apply(json, restored);
        assertTrue(restored.entry("watermark").background);
        assertTrue(restored.entry("watermark").border);
    }

    @Test
    void layerAndResizeOverridesRoundTrip() {
        HudLayout layout = new HudLayout();
        HudLayout.Entry entry = layout.entry("watermark");
        entry.layer = 3;
        entry.width = 120;
        entry.height = 30;
        JsonObject json = HudLayoutStore.toJson(layout);
        HudLayout restored = new HudLayout();
        HudLayoutStore.apply(json, restored);
        assertEquals(3, restored.entry("watermark").layer);
        assertEquals(120, restored.entry("watermark").width);
        assertEquals(30, restored.entry("watermark").height);
    }

    @Test
    void resizeOverridesAreClamped() {
        JsonObject root = new JsonObject();
        root.addProperty("schema", HudLayoutStore.SCHEMA_VERSION);
        JsonObject elements = new JsonObject();
        JsonObject entry = new JsonObject();
        entry.addProperty("width", -50);
        entry.addProperty("height", 99999);
        elements.add("watermark", entry);
        root.add("elements", elements);
        HudLayout layout = new HudLayout();
        HudLayoutStore.apply(root, layout);
        assertEquals(0, layout.entry("watermark").width);
        assertEquals(4096, layout.entry("watermark").height);
    }
}
