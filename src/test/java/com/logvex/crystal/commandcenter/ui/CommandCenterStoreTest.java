package com.logvex.crystal.commandcenter.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.logvex.crystal.commandcenter.quick.QuickAction;
import com.logvex.crystal.commandcenter.quick.QuickActionType;
import com.logvex.crystal.commandcenter.quick.QuickTrigger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Covers the Command Center store: round-trip persistence, tolerant reads and schema refusal. */
class CommandCenterStoreTest {

    private static CommandCenterPreferences samplePreferences() {
        CommandCenterPreferences prefs = new CommandCenterPreferences();
        prefs.defaultWorkspace = CommandCenterPreferences.Workspace.PERFORMANCE;
        prefs.refreshIntervalMillis = 750;
        prefs.hudShowFindings = false;
        prefs.notificationRateLimit = 9;
        return prefs;
    }

    private static QuickAction sampleAction() {
        QuickAction action = new QuickAction("qa-1", "On crash");
        action.enabled(true).trigger(QuickTrigger.CRASH_RECORDED)
                .type(QuickActionType.OPEN_WORKSPACE).argument("CRASHES").cooldownMillis(5000)
                .maxExecutions(3).requiresConfirmation(true);
        return action;
    }

    @Test
    void roundTripsPreferencesLayoutAndActions(@TempDir Path dir) throws IOException {
        CommandCenterStore store = new CommandCenterStore(dir.resolve("command-center.json"));
        DashboardLayout layout = DashboardLayout.defaults();
        layout.toggle(DashboardWidget.AI);
        store.save(samplePreferences(), layout, List.of(sampleAction()));

        CommandCenterStore.Loaded loaded = store.load();
        assertEquals(CommandCenterPreferences.Workspace.PERFORMANCE, loaded.preferences().defaultWorkspace);
        assertEquals(750, loaded.preferences().refreshIntervalMillis);
        assertFalse(loaded.preferences().hudShowFindings);
        assertFalse(loaded.layout().isVisible(DashboardWidget.AI));
        assertEquals(1, loaded.quickActions().size());
        QuickAction loadedAction = loaded.quickActions().get(0);
        assertEquals("qa-1", loadedAction.id());
        assertEquals(QuickTrigger.CRASH_RECORDED, loadedAction.trigger());
        assertEquals(QuickActionType.OPEN_WORKSPACE, loadedAction.type());
        assertEquals("CRASHES", loadedAction.argument());
        assertEquals(5000, loadedAction.cooldownMillis());
        assertEquals(3, loadedAction.maxExecutions());
        assertTrue(loadedAction.requiresConfirmation());
        assertTrue(loadedAction.enabled());
    }

    @Test
    void missingFileYieldsDefaults(@TempDir Path dir) {
        CommandCenterStore store = new CommandCenterStore(dir.resolve("absent.json"));
        CommandCenterStore.Loaded loaded = store.load();
        assertEquals(CommandCenterPreferences.Workspace.DASHBOARD, loaded.preferences().defaultWorkspace);
        assertEquals(DashboardWidget.values().length, loaded.layout().entries().size());
        assertTrue(loaded.quickActions().isEmpty());
    }

    @Test
    void malformedFileYieldsDefaults(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("broken.json");
        Files.writeString(file, "{ this is not json ");
        CommandCenterStore.Loaded loaded = new CommandCenterStore(file).load();
        assertEquals(CommandCenterPreferences.Workspace.DASHBOARD, loaded.preferences().defaultWorkspace);
    }

    @Test
    void newerSchemaIsRefusedRatherThanMisread(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("future.json");
        Files.writeString(file, "{\"schema\":9999,\"defaultWorkspace\":\"AI\"}");
        CommandCenterStore.Loaded loaded = new CommandCenterStore(file).load();
        // Refused: the defaults are used, not the (untrusted) future file.
        assertEquals(CommandCenterPreferences.Workspace.DASHBOARD, loaded.preferences().defaultWorkspace);
        assertTrue(loaded.quickActions().isEmpty());
    }

    @Test
    void actionsWithoutAnIdAreSkipped(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("noid.json");
        Files.writeString(file, "{\"schema\":1,\"quickActions\":[{\"name\":\"nameless\"}]}");
        CommandCenterStore.Loaded loaded = new CommandCenterStore(file).load();
        assertTrue(loaded.quickActions().isEmpty());
    }
}
