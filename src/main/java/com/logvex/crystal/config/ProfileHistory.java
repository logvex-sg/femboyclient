package com.logvex.crystal.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A bounded, append-only log of profile changes: what happened, to which profile and when.
 * Persisted beside the profiles so a switch or delete is auditable after a restart.
 *
 * <p>Kept deliberately small - a fixed number of recent entries in a versioned JSON file -
 * so it cannot grow without bound or slow profile operations down.
 */
public final class ProfileHistory {

    /** How a profile was changed. */
    public enum Action {
        SAVED,
        LOADED,
        CREATED,
        RENAMED,
        DUPLICATED,
        DELETED,
        IMPORTED,
        EXPORTED,
        DEFAULT_SET
    }

    /** One recorded change. */
    public record Entry(Action action, String profile, String detail, long atMillis) {
    }

    private static final int LIMIT = 100;

    private final Path file;
    private final Deque<Entry> entries = new ArrayDeque<>();

    public ProfileHistory(Path file) {
        this.file = file;
        load();
    }

    public Path file() {
        return file;
    }

    /** Records a change and persists it (best effort). */
    public void record(Action action, String profile, String detail) {
        entries.addFirst(new Entry(action, profile == null ? "" : profile, detail == null ? "" : detail,
                System.currentTimeMillis()));
        while (entries.size() > LIMIT) {
            entries.removeLast();
        }
        save();
    }

    /** Recent entries, newest first. */
    public List<Entry> recent() {
        return new ArrayList<>(entries);
    }

    public void clear() {
        entries.clear();
        save();
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("schema", 1);
        JsonArray array = new JsonArray();
        for (Entry entry : entries) {
            JsonObject json = new JsonObject();
            json.addProperty("action", entry.action().name());
            json.addProperty("profile", entry.profile());
            json.addProperty("detail", entry.detail());
            json.addProperty("at", entry.atMillis());
            array.add(json);
        }
        root.add("entries", array);
        return root;
    }

    public void apply(JsonObject root) {
        if (root == null) {
            return;
        }
        int schema = root.has("schema") && root.get("schema").isJsonPrimitive()
                ? root.get("schema").getAsInt() : 1;
        if (schema > 1) {
            return;
        }
        entries.clear();
        if (!root.has("entries") || !root.get("entries").isJsonArray()) {
            return;
        }
        for (var element : root.getAsJsonArray("entries")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject json = element.getAsJsonObject();
            Action action;
            try {
                action = Action.valueOf(json.get("action").getAsString());
            } catch (RuntimeException e) {
                continue;
            }
            entries.addLast(new Entry(action,
                    opt(json, "profile", ""), opt(json, "detail", ""),
                    json.has("at") ? json.get("at").getAsLong() : 0L));
            if (entries.size() >= LIMIT) {
                break;
            }
        }
    }

    private void load() {
        JsonObject root = ConfigIo.readObject(file);
        if (root != null) {
            apply(root);
        }
    }

    private void save() {
        try {
            ConfigIo.writeObject(file, toJson());
        } catch (java.io.IOException ignored) {
            // History is advisory; a failed write must not break the operation.
        }
    }

    private static String opt(JsonObject o, String key, String fallback) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : fallback;
    }
}
