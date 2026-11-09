package com.logvex.crystal.commandcenter.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Covers the Command Center preferences: workspace parsing and clamping. */
class CommandCenterPreferencesTest {

    @Test
    void workspaceParsesNamesCaseInsensitivelyAndFallsBack() {
        assertEquals(CommandCenterPreferences.Workspace.AI,
                CommandCenterPreferences.Workspace.parse("ai"));
        assertEquals(CommandCenterPreferences.Workspace.QUICK_ACTIONS,
                CommandCenterPreferences.Workspace.parse("quick_actions"));
        assertEquals(CommandCenterPreferences.Workspace.DASHBOARD,
                CommandCenterPreferences.Workspace.parse("not-a-workspace"));
        assertEquals(CommandCenterPreferences.Workspace.DASHBOARD,
                CommandCenterPreferences.Workspace.parse(null));
    }

    @Test
    void everyWorkspaceHasALabel() {
        for (CommandCenterPreferences.Workspace workspace : CommandCenterPreferences.Workspace.values()) {
            assertFalse(workspace.label().isBlank());
        }
    }

    @Test
    void clampBoundsIntervalsAndRateLimit() {
        CommandCenterPreferences prefs = new CommandCenterPreferences();
        prefs.refreshIntervalMillis = 1;
        prefs.historyIntervalMillis = 0;
        prefs.notificationCooldownMillis = -5;
        prefs.notificationRateLimit = 999;
        prefs.clamp();
        assertTrue(prefs.refreshIntervalMillis >= 250);
        assertTrue(prefs.historyIntervalMillis >= 250);
        assertTrue(prefs.notificationCooldownMillis >= 1000);
        assertTrue(prefs.notificationRateLimit <= 60);
        assertTrue(prefs.notificationRateLimit >= 1);
    }

    @Test
    void copyIsIndependent() {
        CommandCenterPreferences prefs = new CommandCenterPreferences();
        prefs.defaultWorkspace = CommandCenterPreferences.Workspace.MODULES;
        CommandCenterPreferences copy = prefs.copy();
        assertEquals(CommandCenterPreferences.Workspace.MODULES, copy.defaultWorkspace);
        copy.defaultWorkspace = CommandCenterPreferences.Workspace.AI;
        assertEquals(CommandCenterPreferences.Workspace.MODULES, prefs.defaultWorkspace);
    }
}
