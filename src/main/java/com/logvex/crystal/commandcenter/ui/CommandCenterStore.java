package com.logvex.crystal.commandcenter.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.logvex.crystal.commandcenter.quick.QuickAction;
import com.logvex.crystal.commandcenter.quick.QuickActionType;
import com.logvex.crystal.commandcenter.quick.QuickTrigger;
import com.logvex.crystal.config.ConfigIo;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists the Command Center's own preferences, the dashboard layout and the quick-action
 * definitions to {@code command-center.json}.
 *
 * <p>It reuses {@link ConfigIo}, so writes are atomic (temp file + move) with a {@code .bak}
 * of the previous valid file, and reads tolerate a missing, empty or malformed file by
 * returning defaults rather than throwing. A newer schema version is refused, not guessed at,
 * so a file written by a future build cannot be silently downgraded.
 *
 * <p>Minecraft-independent and unit testable: the store only knows paths and JSON.
 */
public final class CommandCenterStore {

    /** Bumped when the on-disk shape changes; a newer file is refused. */
    public static final int SCHEMA_VERSION = 1;

    /** Everything the store reads back: preferences, the dashboard layout and the quick actions. */
    public record Loaded(CommandCenterPreferences preferences, DashboardLayout layout,
                         List<QuickAction> quickActions) {
    }

    private final Path file;

    public CommandCenterStore(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    /** Reads the store, returning defaults for anything absent or malformed. Never throws. */
    public Loaded load() {
        JsonObject root = ConfigIo.readObject(file);
        if (root == null) {
            return new Loaded(new CommandCenterPreferences(), DashboardLayout.defaults(), List.of());
        }
        int schema = optInt(root, "schema", SCHEMA_VERSION);
        if (schema > SCHEMA_VERSION) {
            // A newer file: keep the defaults rather than risk misreading it.
            return new Loaded(new CommandCenterPreferences(), DashboardLayout.defaults(), List.of());
        }
        CommandCenterPreferences prefs = readPreferences(root);
        DashboardLayout layout = DashboardLayout.decode(optString(root, "dashboardLayout", ""));
        List<QuickAction> quickActions = readQuickActions(root);
        return new Loaded(prefs, layout, quickActions);
    }

    /** Writes the store atomically. */
    public void save(CommandCenterPreferences preferences, DashboardLayout layout,
                     List<QuickAction> quickActions) throws IOException {
        preferences.clamp();
        ConfigIo.writeObject(file, toJson(preferences, layout, quickActions));
    }

    // ------------------------------------------------------------------ codec

    public static JsonObject toJson(CommandCenterPreferences p, DashboardLayout layout,
                                    List<QuickAction> quickActions) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        root.addProperty("defaultWorkspace", p.defaultWorkspace.name());
        root.addProperty("refreshIntervalMillis", p.refreshIntervalMillis);
        root.addProperty("historyIntervalMillis", p.historyIntervalMillis);
        root.addProperty("hudShowFindings", p.hudShowFindings);
        root.addProperty("notificationsEnabled", p.notificationsEnabled);
        root.addProperty("dedupeNotifications", p.dedupeNotifications);
        root.addProperty("notificationCooldownMillis", p.notificationCooldownMillis);
        root.addProperty("notificationRateLimit", p.notificationRateLimit);
        root.addProperty("autoApplyQuickActions", p.autoApplyQuickActions);
        root.addProperty("reducedMotion", p.reducedMotion);
        root.addProperty("dashboardLayout", layout == null ? "" : layout.encode());

        JsonArray rules = new JsonArray();
        if (quickActions != null) {
            for (QuickAction action : quickActions) {
                rules.add(quickActionToJson(action));
            }
        }
        root.add("quickActions", rules);
        return root;
    }

    private static JsonObject quickActionToJson(QuickAction action) {
        JsonObject o = new JsonObject();
        o.addProperty("id", action.id());
        o.addProperty("name", action.name());
        o.addProperty("enabled", action.enabled());
        o.addProperty("trigger", action.trigger().name());
        o.addProperty("section", action.section());
        o.addProperty("type", action.type().name());
        o.addProperty("argument", action.argument());
        o.addProperty("message", action.message());
        o.addProperty("cooldownMillis", action.cooldownMillis());
        o.addProperty("maxExecutions", action.maxExecutions());
        o.addProperty("requiresConfirmation", action.requiresConfirmation());
        return o;
    }

    private static CommandCenterPreferences readPreferences(JsonObject root) {
        CommandCenterPreferences p = new CommandCenterPreferences();
        p.defaultWorkspace = CommandCenterPreferences.Workspace.parse(optString(root, "defaultWorkspace", "DASHBOARD"));
        p.refreshIntervalMillis = optLong(root, "refreshIntervalMillis", p.refreshIntervalMillis);
        p.historyIntervalMillis = optLong(root, "historyIntervalMillis", p.historyIntervalMillis);
        p.hudShowFindings = optBoolean(root, "hudShowFindings", p.hudShowFindings);
        p.notificationsEnabled = optBoolean(root, "notificationsEnabled", p.notificationsEnabled);
        p.dedupeNotifications = optBoolean(root, "dedupeNotifications", p.dedupeNotifications);
        p.notificationCooldownMillis = optLong(root, "notificationCooldownMillis", p.notificationCooldownMillis);
        p.notificationRateLimit = optInt(root, "notificationRateLimit", p.notificationRateLimit);
        p.autoApplyQuickActions = optBoolean(root, "autoApplyQuickActions", p.autoApplyQuickActions);
        p.reducedMotion = optBoolean(root, "reducedMotion", p.reducedMotion);
        p.clamp();
        return p;
    }

    private static List<QuickAction> readQuickActions(JsonObject root) {
        List<QuickAction> result = new ArrayList<>();
        JsonElement element = root.get("quickActions");
        if (element == null || !element.isJsonArray()) {
            return result;
        }
        for (JsonElement item : element.getAsJsonArray()) {
            if (!item.isJsonObject()) {
                continue;
            }
            JsonObject o = item.getAsJsonObject();
            String id = optString(o, "id", "");
            if (id.isBlank()) {
                continue;
            }
            QuickAction action = new QuickAction(id, optString(o, "name", id));
            action.enabled(optBoolean(o, "enabled", false));
            action.trigger(QuickTrigger.parse(optString(o, "trigger", "MANUAL")));
            action.section(optString(o, "section", ""));
            action.type(QuickActionType.parse(optString(o, "type", "SHOW_DIAGNOSTIC")));
            action.argument(optString(o, "argument", ""));
            action.message(optString(o, "message", ""));
            action.cooldownMillis(optLong(o, "cooldownMillis", 30_000L));
            action.maxExecutions(optInt(o, "maxExecutions", 0));
            action.requiresConfirmation(optBoolean(o, "requiresConfirmation", false));
            result.add(action);
        }
        return result;
    }

    // ------------------------------------------------------------------ json helpers

    private static String optString(JsonObject o, String key, String fallback) {
        JsonElement e = o.get(key);
        return e == null || !e.isJsonPrimitive() ? fallback : e.getAsString();
    }

    private static boolean optBoolean(JsonObject o, String key, boolean fallback) {
        JsonElement e = o.get(key);
        return e == null || !e.isJsonPrimitive() ? fallback : e.getAsBoolean();
    }

    private static int optInt(JsonObject o, String key, int fallback) {
        JsonElement e = o.get(key);
        return e == null || !e.isJsonPrimitive() ? fallback : e.getAsInt();
    }

    private static long optLong(JsonObject o, String key, long fallback) {
        JsonElement e = o.get(key);
        return e == null || !e.isJsonPrimitive() ? fallback : e.getAsLong();
    }
}
