package com.logvex.crystal.automation.inventory;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.logvex.crystal.config.ConfigIo;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Persisted hotbar presets: a named mapping from hotbar slot (0..8) to an item id. Same
 * conventions as the other stores - schema-versioned JSON, atomic write, malformed entries
 * skipped rather than aborting the load, and a newer schema refused so a future version's data is
 * not silently downgraded.
 *
 * <p>Deliberately Minecraft-free so the persistence and validation logic is unit tested without a
 * client. The preset only records <em>which</em> item goes in <em>which</em> slot; the module
 * turns that into real hotbar swaps.
 */
public final class HotbarPresetStore {

    public static final int SCHEMA_VERSION = 1;
    public static final int HOTBAR_SIZE = 9;

    /** One preset: a display name and the item id per hotbar slot (a null entry means "any"). */
    public record Preset(String name, String[] slots) {
        public Preset {
            slots = slots == null ? new String[HOTBAR_SIZE] : slots.clone();
        }

        @Override
        public String[] slots() {
            return slots.clone();
        }
    }

    private final Path file;
    private final List<Preset> presets = new ArrayList<>();

    public HotbarPresetStore(Path file) {
        this.file = file;
    }

    /** Preset names in insertion order. */
    public List<String> names() {
        List<String> result = new ArrayList<>();
        for (Preset preset : presets) {
            result.add(preset.name());
        }
        return result;
    }

    public List<Preset> all() {
        return Collections.unmodifiableList(new ArrayList<>(presets));
    }

    /** The preset with this name (case-insensitive), or null. */
    public Preset byName(String name) {
        if (name == null) {
            return null;
        }
        for (Preset preset : presets) {
            if (preset.name().equalsIgnoreCase(name.trim())) {
                return preset;
            }
        }
        return null;
    }

    public boolean has(String name) {
        return byName(name) != null;
    }

    /** Adds or replaces a preset. A null entry means "leave this slot alone". */
    public void put(String name, String[] slots) {
        if (name == null || name.isBlank()) {
            return;
        }
        String[] copy = new String[HOTBAR_SIZE];
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            copy[i] = slots != null && i < slots.length ? slots[i] : null;
        }
        presets.removeIf(existing -> existing.name().equalsIgnoreCase(name.trim()));
        presets.add(new Preset(name.trim(), copy));
    }

    public boolean remove(String name) {
        return presets.removeIf(preset -> preset.name().equalsIgnoreCase(name == null ? "" : name.trim()));
    }

    public void clear() {
        presets.clear();
    }

    public void save() throws IOException {
        ConfigIo.writeObject(file, toJson());
    }

    public void load() {
        JsonObject root = ConfigIo.readObject(file);
        presets.clear();
        if (root == null) {
            return;
        }
        int schema = root.has("schema") ? root.get("schema").getAsInt() : SCHEMA_VERSION;
        if (schema > SCHEMA_VERSION) {
            return;
        }
        JsonArray array = root.has("presets") ? root.getAsJsonArray("presets") : null;
        if (array == null) {
            return;
        }
        for (var element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            if (!entry.has("name") || !entry.has("slots")) {
                continue;
            }
            String[] slots = new String[HOTBAR_SIZE];
            JsonArray slotArray = entry.getAsJsonArray("slots");
            for (int i = 0; i < HOTBAR_SIZE && i < slotArray.size(); i++) {
                if (!slotArray.get(i).isJsonNull()) {
                    slots[i] = slotArray.get(i).getAsString();
                }
            }
            put(entry.get("name").getAsString(), slots);
        }
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        JsonArray array = new JsonArray();
        for (Preset preset : presets) {
            JsonObject object = new JsonObject();
            object.addProperty("name", preset.name());
            JsonArray slots = new JsonArray();
            for (String id : preset.slots()) {
                if (id == null) {
                    slots.add(JsonNull.INSTANCE);
                } else {
                    slots.add(id);
                }
            }
            object.add("slots", slots);
            array.add(object);
        }
        root.add("presets", array);
        return root;
    }

    public int size() {
        return presets.size();
    }

    /** A preset name normalised for comparison. */
    public static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
