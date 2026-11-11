package com.logvex.crystal.storage;

import com.google.gson.JsonObject;
import com.logvex.crystal.config.ConfigIo;
import com.logvex.crystal.config.ConfigPaths;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * User-assigned labels for containers the player has legitimately opened.
 *
 * <p>A label is keyed by the container's dimension and block position, so the same chest in
 * the Overworld and the Nether never collide. Only containers the player actually opens are
 * ever keyed - there is no discovery of containers the player cannot see.
 *
 * <p>Persistence mirrors the other config stores: a versioned object written atomically, and
 * a corrupt file yields an empty map rather than stopping the client. The key formatting and
 * the map handling are Minecraft-independent so they can be unit tested.
 */
public final class ContainerLabels {

    public static final int SCHEMA_VERSION = 1;

    private static volatile ContainerLabels instance;

    private final Path file;
    private final Map<String, String> labels = new LinkedHashMap<>();

    public ContainerLabels(Path file) {
        this.file = file;
    }

    /** The shared store, created on first use so class loading never touches FabricLoader. */
    public static ContainerLabels get() {
        ContainerLabels local = instance;
        if (local == null) {
            synchronized (ContainerLabels.class) {
                local = instance;
                if (local == null) {
                    local = new ContainerLabels(ConfigPaths.containerLabels());
                    instance = local;
                }
            }
        }
        return local;
    }

    public Path file() {
        return file;
    }

    /** A stable key for a container at a position in a dimension. */
    public static String key(String dimension, int x, int y, int z) {
        String dim = dimension == null || dimension.isBlank() ? "unknown" : dimension;
        return dim + "@" + x + "," + y + "," + z;
    }

    public int size() {
        return labels.size();
    }

    /** The label for a key, or an empty string when there is none. */
    public String get(String key) {
        return key == null ? "" : labels.getOrDefault(key, "");
    }

    /** Assigns (or, with a blank label, clears) a container's label. */
    public void set(String key, String label) {
        if (key == null || key.isBlank()) {
            return;
        }
        if (label == null || label.isBlank()) {
            labels.remove(key);
        } else {
            labels.put(key, label.trim());
        }
    }

    public void remove(String key) {
        if (key != null) {
            labels.remove(key);
        }
    }

    public void clear() {
        labels.clear();
    }

    /** Every label, as key to text. */
    public Map<String, String> all() {
        return new LinkedHashMap<>(labels);
    }

    /** Labels whose text contains the query, case-insensitively. A blank query matches all. */
    public List<Map.Entry<String, String>> search(String query) {
        String needle = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        List<Map.Entry<String, String>> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            if (needle.isEmpty() || entry.getValue().toLowerCase(Locale.ROOT).contains(needle)) {
                result.add(Map.entry(entry.getKey(), entry.getValue()));
            }
        }
        return result;
    }

    public void load() {
        JsonObject root = ConfigIo.readObject(file);
        if (root != null) {
            apply(root);
        }
    }

    public void save() {
        try {
            ConfigIo.writeObject(file, toJson());
        } catch (IOException e) {
            // Best effort: a failed label write must never break the client.
        }
    }

    /** Serializes the store (exposed for tests). */
    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        JsonObject map = new JsonObject();
        labels.forEach(map::addProperty);
        root.add("labels", map);
        return root;
    }

    /** Applies a parsed object (exposed for tests). */
    public void apply(JsonObject root) {
        int schema = root.has("schema") && root.get("schema").isJsonPrimitive()
                ? root.get("schema").getAsInt() : SCHEMA_VERSION;
        labels.clear();
        if (schema > SCHEMA_VERSION || !root.has("labels") || !root.get("labels").isJsonObject()) {
            return;
        }
        JsonObject map = root.getAsJsonObject("labels");
        for (String key : map.keySet()) {
            var value = map.get(key);
            if (value != null && value.isJsonPrimitive() && !value.getAsString().isBlank()) {
                labels.put(key, value.getAsString().trim());
            }
        }
    }
}
