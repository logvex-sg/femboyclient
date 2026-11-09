package com.logvex.crystal.config;

import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Resolves the client's configuration directory ({@code <game>/config/logvex-crystal/}). */
public final class ConfigPaths {

    private ConfigPaths() {
    }

    public static Path directory() {
        return FabricLoader.getInstance().getConfigDir().resolve("logvex-crystal");
    }

    public static Path appearance() {
        return directory().resolve("appearance.json");
    }

    public static Path modules() {
        return directory().resolve("modules.json");
    }

    public static Path hud() {
        return directory().resolve("hud.json");
    }

    public static Path friends() {
        return directory().resolve("friends.json");
    }

    public static Path waypoints() {
        return directory().resolve("waypoints.json");
    }

    public static Path favorites() {
        return directory().resolve("favorites.json");
    }

    public static Path notifications() {
        return directory().resolve("notifications.json");
    }

    public static Path context() {
        return directory().resolve("context.json");
    }

    /** Persisted automation rules. */
    public static Path automation() {
        return directory().resolve("automation.json");
    }

    /** Persisted extension approval decisions. */
    public static Path extensions() {
        return directory().resolve("extensions.json");
    }

    /** Persisted world-intelligence observations, route history and AI summaries. */
    public static Path worldIntelligence() {
        return directory().resolve("world-intelligence.json");
    }

    /** Persisted world-scanner sessions and scan profiles. */
    public static Path scanSessions() {
        return directory().resolve("scan-sessions.json");
    }

    /** Directory holding named configuration profiles. */
    public static Path profiles() {
        return directory().resolve("profiles");
    }

    /** Phase 7 mini-AI preferences. */
    public static Path ai() {
        return directory().resolve("ai.json");
    }

    /** Phase 8B Command Center preferences, dashboard layout and quick actions. */
    public static Path commandCenter() {
        return directory().resolve("command-center.json");
    }

    /** Directory holding optional local models and the crash/report stores. */
    public static Path aiDirectory() {
        return directory().resolve("ai");
    }

    /** Persisted crash history (crash intelligence). */
    public static Path aiCrashes() {
        return aiDirectory().resolve("crashes.json");
    }

    /** Persisted queue of prepared bug reports. */
    public static Path aiReports() {
        return aiDirectory().resolve("reports.json");
    }

    /** Directory holding downloaded local model files. */
    public static Path aiModels() {
        return aiDirectory().resolve("models");
    }
}
