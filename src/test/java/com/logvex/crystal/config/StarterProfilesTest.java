package com.logvex.crystal.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The starter profiles must be well-formed and name-stable. */
class StarterProfilesTest {

    @Test
    void everyStarterBuildsWithModulesAndAppearance() {
        for (String name : StarterProfiles.names()) {
            ProfileManager.Profile profile = StarterProfiles.build(name);
            assertNotNull(profile, "starter missing: " + name);
            assertTrue(profile.modules().size() > 0, "starter has no modules: " + name);
            assertTrue(profile.appearance().has("preset"), "starter has no preset: " + name);
        }
    }

    @Test
    void unknownStarterIsNull() {
        assertNull(StarterProfiles.build("does-not-exist"));
    }

    @Test
    void minimalHudDisablesTheNoisyElements() {
        ProfileManager.Profile profile = StarterProfiles.build("Minimal HUD");
        assertTrue(profile.modules().getAsJsonObject("ModuleList").get("enabled").getAsBoolean() == false);
        assertTrue(profile.modules().getAsJsonObject("Coords").get("enabled").getAsBoolean());
    }

    @Test
    void startersSerializeToTheProfileShape() {
        ProfileManager.Profile profile = StarterProfiles.build("Combat");
        var json = ProfileManager.toJson(profile);
        assertEquals(ProfileManager.SCHEMA_VERSION, json.get("schema").getAsInt());
        assertNotNull(ProfileManager.fromJson(json));
    }

    @Test
    void everyStarterReferencesRealRegisteredModules() {
        var manager = new com.logvex.crystal.module.ModuleManager();
        com.logvex.crystal.module.ModuleRegistry.registerAll(manager);
        for (String name : StarterProfiles.names()) {
            ProfileManager.Profile profile = StarterProfiles.build(name);
            for (String module : profile.modules().keySet()) {
                assertNotNull(manager.byName(module),
                        "starter '" + name + "' references unknown module '" + module + "'");
            }
        }
    }

    @Test
    void theRequestedPresetCatalogueIsPresent() {
        for (String name : new String[]{"General Survival", "Creative Building", "Exploration",
                "PvP", "Performance", "Minimal HUD", "Full HUD", "Accessibility"}) {
            assertTrue(StarterProfiles.names().contains(name), "missing preset: " + name);
            assertNotNull(StarterProfiles.build(name), "preset did not build: " + name);
        }
    }

    @Test
    void theAccessibilityPresetCalmsMotionAndScalesTheUi() {
        var appearance = StarterProfiles.build("Accessibility").appearance();
        assertTrue(appearance.get("reducedMotion").getAsBoolean());
        assertEquals(0.0f, appearance.get("animationSpeed").getAsFloat());
        assertEquals(1.25f, appearance.get("uiScale").getAsFloat());
    }

    @Test
    void thePerformancePresetDisablesTheCostlyEffects() {
        var appearance = StarterProfiles.build("Performance").appearance();
        assertTrue(!appearance.get("blur").getAsBoolean());
        assertEquals(0.0f, appearance.get("gradientIntensity").getAsFloat());
        assertEquals(0.0f, appearance.get("glowIntensity").getAsFloat());
    }
}
