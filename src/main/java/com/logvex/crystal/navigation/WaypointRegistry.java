package com.logvex.crystal.navigation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.logvex.crystal.config.ConfigIo;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Side metadata for waypoints (category, group, notes) plus a small bookmark list.
 *
 * <p>The {@code WaypointStore} stays the single source of truth for coordinates; this registry
 * only attaches organisation to a waypoint <em>by name</em>, so adding categories never changes
 * the waypoint format or the ESP renderer. A waypoint with no entry simply reports the default
 * category, which keeps the two stores independent: deleting a waypoint leaves harmless metadata
 * behind, and deleting metadata never touches a coordinate.
 *
 * <p>Deliberately Minecraft-free so the organisation logic is unit tested without a client.
 */
public final class WaypointRegistry {

    public static final int SCHEMA_VERSION = 1;
    /** The category a waypoint belongs to when nothing else is set. */
    public static final String DEFAULT_CATEGORY = "general";

    /** Organisation attached to a waypoint name. */
    public record Meta(String category, String group, String notes) {
        public Meta {
            category = category == null || category.isBlank()
                    ? DEFAULT_CATEGORY : category.toLowerCase(Locale.ROOT);
            group = group == null ? "" : group.toLowerCase(Locale.ROOT);
            notes = notes == null ? "" : notes;
        }
    }

    /** A named coordinate that is not a full waypoint: quick bookmarks and last positions. */
    public record Bookmark(String name, int x, int y, int z, String dimension) {
        public Bookmark {
            dimension = dimension == null ? "" : dimension.toLowerCase(Locale.ROOT);
        }
    }

    private final Path file;
    private final Map<String, Meta> metadata = new LinkedHashMap<>();
    private final Map<String, Bookmark> bookmarks = new LinkedHashMap<>();

    public WaypointRegistry(Path file) {
        this.file = file;
    }

    private static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    public Meta metaOf(String name) {
        return metadata.getOrDefault(key(name), new Meta(DEFAULT_CATEGORY, "", ""));
    }

    /** Sets the category/group/notes for a waypoint name, replacing any previous entry. */
    public void setMeta(String name, String category, String group, String notes) {
        if (name == null || name.isBlank()) {
            return;
        }
        metadata.put(key(name), new Meta(category, group, notes));
    }

    public boolean removeMeta(String name) {
        return metadata.remove(key(name)) != null;
    }

    /** Renames the metadata key when a waypoint is renamed, so organisation follows the point. */
    public void rename(String from, String to) {
        Meta meta = metadata.remove(key(from));
        if (meta != null) {
            metadata.put(key(to), meta);
        }
    }

    /** Every category in use, plus the default, sorted. */
    public Set<String> categories() {
        Set<String> set = new LinkedHashSet<>();
        set.add(DEFAULT_CATEGORY);
        List<String> sorted = new ArrayList<>();
        for (Meta meta : metadata.values()) {
            sorted.add(meta.category());
        }
        Collections.sort(sorted);
        set.addAll(sorted);
        return set;
    }

    /** Every non-empty group in use, sorted. */
    public Set<String> groups() {
        Set<String> set = new LinkedHashSet<>();
        List<String> sorted = new ArrayList<>();
        for (Meta meta : metadata.values()) {
            if (!meta.group().isEmpty()) {
                sorted.add(meta.group());
            }
        }
        Collections.sort(sorted);
        set.addAll(sorted);
        return set;
    }

    public Map<String, Meta> all() {
        return Collections.unmodifiableMap(metadata);
    }

    // ------------------------------------------------------------- bookmarks

    public void putBookmark(Bookmark bookmark) {
        if (bookmark == null || bookmark.name().isBlank()) {
            return;
        }
        bookmarks.put(key(bookmark.name()), bookmark);
    }

    public Bookmark bookmark(String name) {
        return bookmarks.get(key(name));
    }

    public boolean removeBookmark(String name) {
        return bookmarks.remove(key(name)) != null;
    }

    public List<Bookmark> bookmarks() {
        return List.copyOf(bookmarks.values());
    }

    // ------------------------------------------------------------ persistence

    public void save() throws IOException {
        ConfigIo.writeObject(file, toJson());
    }

    public void load() {
        JsonObject root = ConfigIo.readObject(file);
        if (root != null) {
            apply(root);
        }
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        JsonArray metas = new JsonArray();
        for (Map.Entry<String, Meta> entry : metadata.entrySet()) {
            JsonObject object = new JsonObject();
            object.addProperty("name", entry.getKey());
            object.addProperty("category", entry.getValue().category());
            object.addProperty("group", entry.getValue().group());
            object.addProperty("notes", entry.getValue().notes());
            metas.add(object);
        }
        root.add("metadata", metas);
        JsonArray marks = new JsonArray();
        for (Bookmark bookmark : bookmarks.values()) {
            JsonObject object = new JsonObject();
            object.addProperty("name", bookmark.name());
            object.addProperty("x", bookmark.x());
            object.addProperty("y", bookmark.y());
            object.addProperty("z", bookmark.z());
            object.addProperty("dimension", bookmark.dimension());
            marks.add(object);
        }
        root.add("bookmarks", marks);
        return root;
    }

    /** Applies a parsed document; a newer schema is refused so data is never downgraded. */
    public void apply(JsonObject root) {
        if (root.has("schema") && root.get("schema").isJsonPrimitive()
                && root.get("schema").getAsInt() > SCHEMA_VERSION) {
            return;
        }
        metadata.clear();
        bookmarks.clear();
        if (root.has("metadata") && root.get("metadata").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("metadata")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject object = element.getAsJsonObject();
                try {
                    metadata.put(key(object.get("name").getAsString()), new Meta(
                            object.has("category") ? object.get("category").getAsString() : DEFAULT_CATEGORY,
                            object.has("group") ? object.get("group").getAsString() : "",
                            object.has("notes") ? object.get("notes").getAsString() : ""));
                } catch (RuntimeException malformed) {
                    // Skip this entry, keep the rest.
                }
            }
        }
        if (root.has("bookmarks") && root.get("bookmarks").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("bookmarks")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject object = element.getAsJsonObject();
                try {
                    Bookmark bookmark = new Bookmark(object.get("name").getAsString(),
                            object.get("x").getAsInt(), object.get("y").getAsInt(),
                            object.get("z").getAsInt(),
                            object.has("dimension") ? object.get("dimension").getAsString() : "");
                    bookmarks.put(key(bookmark.name()), bookmark);
                } catch (RuntimeException malformed) {
                    // Skip this entry, keep the rest.
                }
            }
        }
    }
}
