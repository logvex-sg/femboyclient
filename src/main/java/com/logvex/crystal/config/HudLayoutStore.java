package com.logvex.crystal.config;

import com.google.gson.JsonObject;
import com.logvex.crystal.gui.HudLayout;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Persists per-element HUD placement (position, visibility, opacity, scale) to
 * {@code <config>/hud.json}.
 *
 * <p>Kept separate from the module and appearance configs so dragging a HUD element never
 * rewrites either. Writes are atomic and the schema is versioned; unknown or newer files
 * are ignored rather than misread. The codec is Minecraft-independent so it can be tested.
 */
public final class HudLayoutStore {

    /** Schema version of the on-disk format. Bump when the field set changes shape. */
    public static final int SCHEMA_VERSION = 1;


    private final Path file;

    public HudLayoutStore(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    /** Reads stored placements into {@code layout}. Absent/corrupt files leave it untouched. */
    public void loadInto(HudLayout layout) {
        JsonObject root = ConfigIo.readObject(file);
        if (root != null) {
            apply(root, layout);
        }
    }

    /** Writes every entry in {@code layout} atomically, keeping a backup of the previous file. */
    public void save(HudLayout layout) throws IOException {
        ConfigIo.writeObject(file, toJson(layout));
    }

    /** Serializes a layout (exposed for tests and diagnostics). */
    public static JsonObject toJson(HudLayout layout) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        JsonObject elements = new JsonObject();
        for (Map.Entry<String, HudLayout.Entry> e : layout.all().entrySet()) {
            HudLayout.Entry entry = e.getValue();
            JsonObject json = new JsonObject();
            json.addProperty("visible", entry.visible);
            json.addProperty("customPosition", entry.customPosition);
            json.addProperty("x", entry.x);
            json.addProperty("y", entry.y);
            json.addProperty("opacity", entry.opacity);
            json.addProperty("scale", entry.scale);
            json.addProperty("background", entry.background);
            json.addProperty("border", entry.border);
            json.addProperty("layer", entry.layer);
            json.addProperty("width", entry.width);
            json.addProperty("height", entry.height);
            elements.add(e.getKey(), json);
        }
        root.add("elements", elements);
        return root;
    }

    /** Applies a parsed layout object. Exposed for tests. */
    public static void apply(JsonObject root, HudLayout layout) {
        int schema = root.has("schema") && root.get("schema").isJsonPrimitive()
                ? root.get("schema").getAsInt() : SCHEMA_VERSION;
        if (schema > SCHEMA_VERSION) {
            return;
        }
        if (!root.has("elements") || !root.get("elements").isJsonObject()) {
            return;
        }
        JsonObject elements = root.getAsJsonObject("elements");
        for (String id : elements.keySet()) {
            if (!elements.get(id).isJsonObject()) {
                continue;
            }
            JsonObject json = elements.getAsJsonObject(id);
            HudLayout.Entry entry = layout.entry(id);
            entry.visible = optBool(json, "visible", entry.visible);
            entry.customPosition = optBool(json, "customPosition", entry.customPosition);
            entry.x = optInt(json, "x", entry.x);
            entry.y = optInt(json, "y", entry.y);
            entry.opacity = clamp(optFloat(json, "opacity", entry.opacity), 0f, 1f);
            entry.scale = clamp(optFloat(json, "scale", entry.scale), 0.5f, 2f);
            entry.background = optBool(json, "background", entry.background);
            entry.border = optBool(json, "border", entry.border);
            entry.layer = optInt(json, "layer", entry.layer);
            entry.width = clampInt(optInt(json, "width", entry.width), 0, 4096);
            entry.height = clampInt(optInt(json, "height", entry.height), 0, 4096);
        }
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int optInt(JsonObject o, String key, int fallback) {
        try {
            return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsInt() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static float optFloat(JsonObject o, String key, float fallback) {
        try {
            return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsFloat() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static boolean optBool(JsonObject o, String key, boolean fallback) {
        try {
            return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsBoolean() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
