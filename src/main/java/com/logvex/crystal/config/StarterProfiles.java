package com.logvex.crystal.config;

import com.google.gson.JsonObject;
import com.logvex.crystal.theme.ThemePreset;
import java.util.List;

/**
 * Curated starter profiles shipped with the client.
 *
 * <p>Each is built as plain JSON in the same shape a saved profile uses, so applying one
 * goes through exactly the same path as any user profile. Only enabled flags and the theme
 * are set; unknown module names are skipped by the loader, so a starter keeps working even
 * if a module is renamed or removed.
 *
 * <p>These are templates the user loads, never auto-applied - installing the client never
 * silently changes the user's configuration.
 */
public final class StarterProfiles {

    private StarterProfiles() {
    }

    /** Names of every starter, in display order. */
    public static List<String> names() {
        return List.of("Combat", "PvP", "Anarchy Survival", "General Survival", "Creative Building",
                "Exploration", "Performance", "Minimal HUD", "Full HUD", "Accessibility",
                "Pastel Aesthetics");
    }

    /** Builds the named starter, or null when the name is unknown. */
    public static ProfileManager.Profile build(String name) {
        return switch (name) {
            case "Combat" -> combat();
            case "PvP" -> pvp();
            case "Anarchy Survival" -> anarchy();
            case "General Survival" -> survival();
            case "Creative Building" -> creative();
            case "Exploration" -> exploration();
            case "Performance" -> performance();
            case "Minimal HUD" -> minimalHud();
            case "Full HUD" -> fullHud();
            case "Accessibility" -> accessibility();
            case "Pastel Aesthetics" -> pastel();
            default -> null;
        };
    }

    private static ProfileManager.Profile combat() {
        JsonObject modules = new JsonObject();
        for (String name : new String[]{"AutoCrystal", "AutoTotem", "CritAssist", "Sprint",
                "CrystalEsp", "EntityEsp", "Tracers", "TargetHud", "CrystalDamage"}) {
            modules.add(name, enabled(true));
        }
        return new ProfileManager.Profile("combat", modules, new JsonObject(),
                appearance(ThemePreset.COTTON_CANDY, true));
    }

    /** Crystal PvP: the combat set plus the survival and automation aids a fight needs. */
    private static ProfileManager.Profile pvp() {
        JsonObject modules = new JsonObject();
        for (String name : new String[]{"AutoCrystal", "AutoTotem", "AutoTotem+", "CritAssist",
                "Sprint", "AutoSprint", "AutoGapple", "HoverTotem", "Surround", "Burrow",
                "AutoArmor", "AutoWeapon+", "CrystalEsp", "EntityEsp", "Tracers", "TargetHud",
                "CrystalDamage", "Potion", "Armor", "DurabilityAlert"}) {
            modules.add(name, enabled(true));
        }
        return new ProfileManager.Profile("pvp", modules, new JsonObject(),
                appearance(ThemePreset.SAKURA_CYBER, true));
    }

    private static ProfileManager.Profile anarchy() {
        JsonObject modules = new JsonObject();
        for (String name : new String[]{"AntiAfk", "AutoReconnect", "ChestLogger", "StorageEsp",
                "Search", "WaypointEsp", "Coords", "Watermark", "AutoEat", "FastPlace"}) {
            modules.add(name, enabled(true));
        }
        return new ProfileManager.Profile("anarchy-survival", modules, new JsonObject(),
                appearance(ThemePreset.MIDNIGHT_KITTY, true));
    }

    /** Ordinary survival: the automation and safety aids, no combat triggers. */
    private static ProfileManager.Profile survival() {
        JsonObject modules = new JsonObject();
        for (String name : new String[]{"AutoEat", "AutoEat+", "AutoTotem", "AutoArmor",
                "AutoTool+", "AutoRefill", "AutoReplenish", "AutoPotion", "SafeWalk", "AutoJump",
                "AutoSwim", "AutoSprint", "AutoRespawn", "AutoGG", "AfkGuard", "SessionStats+",
                "DurabilityAlert", "Armor", "Coords", "Biome", "Time", "Light", "WorldInfo"}) {
            modules.add(name, enabled(true));
        }
        return new ProfileManager.Profile("general-survival", modules, new JsonObject(),
                appearance(ThemePreset.SOFT_BOY, true));
    }

    /** Building: the world tools, build planner and the placement aids. */
    private static ProfileManager.Profile creative() {
        JsonObject modules = new JsonObject();
        for (String name : new String[]{"BuildPlanner", "FastPlace", "Fullbright", "WorldScanner",
                "WorldInfo", "Coords", "Light", "Biome", "AutoTool+", "ContainerTransfer",
                "RestockPlanner", "ShulkerOrganizer"}) {
            modules.add(name, enabled(true));
        }
        return new ProfileManager.Profile("creative-building", modules, new JsonObject(),
                appearance(ThemePreset.LAVENDER_DREAM, true));
    }

    /** Exploration: navigation, mapping and the discovery scanner. */
    private static ProfileManager.Profile exploration() {
        JsonObject modules = new JsonObject();
        for (String name : new String[]{"Navigator", "Baritone", "BaritoneRoute", "WaypointEsp",
                "WorldScanner", "Search", "Coords", "Biome", "Time", "WorldInfo", "SessionStats+",
                "AutoReconnect"}) {
            modules.add(name, enabled(true));
        }
        return new ProfileManager.Profile("exploration", modules, new JsonObject(),
                appearance(ThemePreset.FEMBOY, true));
    }

    /**
     * Performance: only the cheap always-on aids, with the visual effects that cost the most
     * turned off in the appearance block (blur, decorations, gradients, glow).
     */
    private static ProfileManager.Profile performance() {
        JsonObject modules = new JsonObject();
        for (String name : new String[]{"Performance", "Coords", "Watermark", "AutoSprint",
                "SessionStats+"}) {
            modules.add(name, enabled(true));
        }
        JsonObject appearance = appearance(ThemePreset.DEFAULT, false);
        appearance.addProperty("blur", false);
        appearance.addProperty("gradientIntensity", 0.0f);
        appearance.addProperty("glowIntensity", 0.0f);
        appearance.addProperty("catCosmetics", false);
        appearance.addProperty("customCursor", false);
        appearance.addProperty("splashText", false);
        return new ProfileManager.Profile("performance", modules, new JsonObject(), appearance);
    }

    private static ProfileManager.Profile minimalHud() {
        JsonObject modules = new JsonObject();
        for (String name : new String[]{"Coords", "Watermark"}) {
            modules.add(name, enabled(true));
        }
        for (String name : new String[]{"ModuleList", "TargetHud", "CrystalDamage", "Performance"}) {
            modules.add(name, enabled(false));
        }
        return new ProfileManager.Profile("minimal-hud", modules, new JsonObject(),
                appearance(ThemePreset.LAVENDER_DREAM, false));
    }

    /** Full HUD: every informational readout switched on. */
    private static ProfileManager.Profile fullHud() {
        JsonObject modules = new JsonObject();
        for (String name : new String[]{"Watermark", "Coords", "Biome", "Time", "Light",
                "Network", "Performance", "Armor", "Potion", "Speed", "Entities", "EntityCounter",
                "SessionStats+", "TargetHud", "CrystalDamage", "Keystrokes", "ModuleList",
                "CommandCenter"}) {
            modules.add(name, enabled(true));
        }
        return new ProfileManager.Profile("full-hud", modules, new JsonObject(),
                appearance(ThemePreset.SAKURA_PINK, true));
    }

    /**
     * Accessibility: reduced motion, no blur or decorations, a larger UI scale and the
     * always-visible readouts, so motion-sensitive or low-vision users get a calm layout.
     */
    private static ProfileManager.Profile accessibility() {
        JsonObject modules = new JsonObject();
        for (String name : new String[]{"Coords", "Biome", "Time", "Light", "Armor", "Potion",
                "DurabilityAlert", "SessionStats+", "Watermark"}) {
            modules.add(name, enabled(true));
        }
        JsonObject appearance = appearance(ThemePreset.DEFAULT, false);
        appearance.addProperty("reducedMotion", true);
        appearance.addProperty("animationSpeed", 0.0f);
        appearance.addProperty("blur", false);
        appearance.addProperty("gradientIntensity", 0.0f);
        appearance.addProperty("glowIntensity", 0.0f);
        appearance.addProperty("uiScale", 1.25f);
        return new ProfileManager.Profile("accessibility", modules, new JsonObject(), appearance);
    }

    private static ProfileManager.Profile pastel() {
        JsonObject modules = new JsonObject();
        for (String name : new String[]{"Watermark", "Coords", "TargetHud", "CrystalDamage"}) {
            modules.add(name, enabled(true));
        }
        return new ProfileManager.Profile("pastel-aesthetics", modules, new JsonObject(),
                appearance(ThemePreset.SAKURA_FEMBOY, true));
    }

    /** One module entry: {@code {"enabled": bool, "settings": {}}}. */
    private static JsonObject enabled(boolean value) {
        JsonObject entry = new JsonObject();
        entry.addProperty("enabled", value);
        entry.add("settings", new JsonObject());
        return entry;
    }

    private static JsonObject appearance(ThemePreset preset, boolean decorations) {
        JsonObject appearance = new JsonObject();
        appearance.addProperty("schema", AppearanceStore.SCHEMA_VERSION);
        appearance.addProperty("preset", preset.name());
        appearance.addProperty("variant", "DARK");
        appearance.addProperty("decorations", decorations);
        return appearance;
    }
}
