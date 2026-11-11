package com.logvex.crystal.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sidecar metadata for saved profiles: a human description, a category, free-form tags, a
 * per-server/per-world association and the designated default.
 *
 * <p>Kept in its own file ({@code profiles/meta.json}) rather than inside each profile so the
 * profile document format stays exactly what it was - an older client reads and writes
 * profiles unchanged, and metadata is simply ignored by it. Metadata is keyed by the
 * sanitized profile name, so it follows renames only when the caller updates it, and stale
 * entries are harmless.
 *
 * <p>Versioned schema, atomic writes and a Minecraft-independent codec so it is testable.
 */
public final class ProfileMetadataStore {

    /** Schema version of the on-disk format. */
    public static final int SCHEMA_VERSION = 1;

    /** Everything known about a profile beyond its configuration sections. */
    public static final class Metadata {
        public String description = "";
        public String category = "general";
        public final List<String> tags = new ArrayList<>();
        /** A server address or world name this profile is intended for; empty for none. */
        public String context = "";
        /** True for the single profile marked as the startup default. */
        public boolean isDefault;

        public Metadata copy() {
            Metadata copy = new Metadata();
            copy.description = description;
            copy.category = category;
            copy.tags.addAll(tags);
            copy.context = context;
            copy.isDefault = isDefault;
            return copy;
        }
    }

    public static final String DEFAULT_CATEGORY = "general";

    private final Path file;
    private final Map<String, Metadata> entries = new LinkedHashMap<>();
    private String defaultProfile = "";

    public ProfileMetadataStore(Path file) {
        this.file = file;
        load();
    }

    public Path file() {
        return file;
    }

    /** Metadata for a profile, creating an empty entry on first access. */
    public Metadata of(String name) {
        return entries.computeIfAbsent(ProfileManager.sanitize(name), k -> new Metadata());
    }

    /** Metadata for a profile without creating an entry. */
    public Metadata peek(String name) {
        return entries.get(ProfileManager.sanitize(name));
    }

    public String category(String name) {
        Metadata meta = peek(name);
        return meta == null ? DEFAULT_CATEGORY : meta.category;
    }

    public String description(String name) {
        Metadata meta = peek(name);
        return meta == null ? "" : meta.description;
    }

    /** The designated startup default, or empty when none is set. */
    public String defaultProfile() {
        return defaultProfile;
    }

    /**
     * Marks a profile as the startup default, clearing the flag from any other profile so
     * there is at most one. Passing an empty name clears the default.
     */
    public void setDefault(String name) {
        for (Metadata meta : entries.values()) {
            meta.isDefault = false;
        }
        if (name == null || name.isBlank()) {
            defaultProfile = "";
            return;
        }
        String sanitized = ProfileManager.sanitize(name);
        defaultProfile = sanitized;
        of(sanitized).isDefault = true;
    }

    /** Every distinct category in use, plus {@link #DEFAULT_CATEGORY}. */
    public List<String> categories() {
        List<String> result = new ArrayList<>();
        result.add(DEFAULT_CATEGORY);
        for (Metadata meta : entries.values()) {
            if (!result.contains(meta.category)) {
                result.add(meta.category);
            }
        }
        return result;
    }

    /** Profile names whose context matches the given server address or world name. */
    public List<String> forContext(String context) {
        List<String> result = new ArrayList<>();
        if (context == null || context.isBlank()) {
            return result;
        }
        String needle = context.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Metadata> e : entries.entrySet()) {
            String value = e.getValue().context;
            if (!value.isBlank() && needle.contains(value.toLowerCase(Locale.ROOT))) {
                result.add(e.getKey());
            }
        }
        return result;
    }

    /** Copies metadata from an old name to a new one and drops the old entry. */
    public void rename(String from, String to) {
        Metadata meta = entries.remove(ProfileManager.sanitize(from));
        if (meta != null) {
            entries.put(ProfileManager.sanitize(to), meta);
        }
        if (defaultProfile.equals(ProfileManager.sanitize(from))) {
            defaultProfile = ProfileManager.sanitize(to);
        }
    }

    /** Drops the metadata for a profile (called when the profile is deleted). */
    public void remove(String name) {
        entries.remove(ProfileManager.sanitize(name));
        if (defaultProfile.equals(ProfileManager.sanitize(name))) {
            defaultProfile = "";
        }
    }

    /** Drops metadata for profiles that no longer exist. */
    public void prune(List<String> existing) {
        List<String> sanitized = new ArrayList<>();
        for (String name : existing) {
            sanitized.add(ProfileManager.sanitize(name));
        }
        entries.keySet().removeIf(key -> !sanitized.contains(key));
        if (!defaultProfile.isEmpty() && !sanitized.contains(defaultProfile)) {
            defaultProfile = "";
        }
    }

    /** Serializes the store (exposed for tests). */
    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        root.addProperty("default", defaultProfile);
        JsonObject profiles = new JsonObject();
        for (Map.Entry<String, Metadata> e : entries.entrySet()) {
            Metadata meta = e.getValue();
            JsonObject json = new JsonObject();
            json.addProperty("description", meta.description);
            json.addProperty("category", meta.category);
            json.addProperty("context", meta.context);
            JsonArray tags = new JsonArray();
            meta.tags.forEach(tags::add);
            json.add("tags", tags);
            profiles.add(e.getKey(), json);
        }
        root.add("profiles", profiles);
        return root;
    }

    /** Applies a parsed store (exposed for tests). A newer schema is ignored. */
    public void apply(JsonObject root) {
        if (root == null) {
            return;
        }
        int schema = root.has("schema") && root.get("schema").isJsonPrimitive()
                ? root.get("schema").getAsInt() : SCHEMA_VERSION;
        if (schema > SCHEMA_VERSION) {
            return;
        }
        entries.clear();
        defaultProfile = root.has("default") && root.get("default").isJsonPrimitive()
                ? root.get("default").getAsString() : "";
        if (!root.has("profiles") || !root.get("profiles").isJsonObject()) {
            return;
        }
        JsonObject profiles = root.getAsJsonObject("profiles");
        for (String key : profiles.keySet()) {
            if (!profiles.get(key).isJsonObject()) {
                continue;
            }
            JsonObject json = profiles.getAsJsonObject(key);
            Metadata meta = new Metadata();
            meta.description = optString(json, "description", "");
            meta.category = optString(json, "category", DEFAULT_CATEGORY);
            meta.context = optString(json, "context", "");
            if (json.has("tags") && json.get("tags").isJsonArray()) {
                for (JsonElement tag : json.getAsJsonArray("tags")) {
                    if (tag.isJsonPrimitive()) {
                        meta.tags.add(tag.getAsString());
                    }
                }
            }
            meta.isDefault = key.equals(defaultProfile);
            entries.put(key, meta);
        }
    }

    private void load() {
        JsonObject root = ConfigIo.readObject(file);
        if (root != null) {
            apply(root);
        }
    }

    /** Persists the store atomically (best effort; a failure is logged by the caller). */
    public void save() {
        try {
            ConfigIo.writeObject(file, toJson());
        } catch (java.io.IOException ignored) {
            // Metadata is a convenience layer; a failed write must not break profile use.
        }
    }

    private static String optString(JsonObject o, String key, String fallback) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : fallback;
    }
}
