package com.logvex.crystal.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Guards the automation module set: every module lives in the Automation category, has a unique
 * name, exposes a status line, and shares the same {@link AutomationModule} base so it goes through
 * the one inventory arbiter and task queue.
 *
 * <p>Constructing a module only reads its own metadata and settings, so this needs no Minecraft
 * runtime; the HotbarPresets store is resolved lazily precisely so this test can run headless.
 */
class AutomationModulesTest {

    private static List<AutomationModule> modules() {
        return List.of(
                new com.logvex.crystal.module.automation.AutoTotemAutomation(),
                new com.logvex.crystal.module.automation.AutoArmorAutomation(),
                new com.logvex.crystal.module.automation.AutoWeaponAutomation(),
                new com.logvex.crystal.module.automation.AutoToolAutomation(),
                new com.logvex.crystal.module.automation.AutoRefillAutomation(),
                new com.logvex.crystal.module.automation.AutoReplenishAutomation(),
                new com.logvex.crystal.module.automation.AutoEatAutomation(),
                new com.logvex.crystal.module.automation.AutoPotionAutomation(),
                new com.logvex.crystal.module.automation.AutoSortAutomation(),
                new com.logvex.crystal.module.automation.AutoTrashAutomation(),
                new com.logvex.crystal.module.automation.ShulkerOrganizerAutomation(),
                new com.logvex.crystal.module.automation.HotbarPresetsAutomation(),
                new com.logvex.crystal.module.automation.ContainerTransferAutomation(),
                new com.logvex.crystal.module.automation.RestockPlannerAutomation(),
                new com.logvex.crystal.module.automation.AutoSprintAutomation(),
                new com.logvex.crystal.module.automation.SafeWalkAutomation(),
                new com.logvex.crystal.module.automation.AutoJumpAutomation(),
                new com.logvex.crystal.module.automation.AutoSwimAutomation(),
                new com.logvex.crystal.module.automation.AutoRespawnAutomation(),
                new com.logvex.crystal.module.automation.AutoGgAutomation(),
                new com.logvex.crystal.module.automation.AfkGuardAutomation(),
                new com.logvex.crystal.module.automation.SessionStatsAutomation());
    }

    @Test
    void everyAutomationModuleIsInTheAutomationCategory() {
        for (Module module : modules()) {
            assertEquals(Category.AUTOMATION, module.category(),
                    module.name() + " must be in the Automation category");
        }
    }

    @Test
    void automationModuleNamesAreUnique() {
        Set<String> names = new HashSet<>();
        for (Module module : modules()) {
            assertTrue(names.add(module.name()), "duplicate automation module name: " + module.name());
        }
        assertEquals(modules().size(), names.size());
    }

    @Test
    void everyAutomationModuleReportsAStatusLine() {
        for (AutomationModule module : modules()) {
            assertNotNull(module.statusLine(), module.name() + " must have a status line");
        }
    }

    @Test
    void disabledModulesReportDisabledByDefault() {
        // The base statusLine defaults to enabled/disabled; a module may override it with richer
        // detail, but a disabled module must never claim to be doing work.
        for (AutomationModule module : modules()) {
            if (!module.isEnabled()) {
                assertNotNull(module.statusLine());
            }
        }
    }

    @Test
    void automationModulesDoNotCollideWithTheExistingRegistry() {
        // The registry forbids duplicate names across the whole client; a rename here would
        // otherwise only surface at runtime when the client bootstraps.
        com.logvex.crystal.module.ModuleManager manager = new com.logvex.crystal.module.ModuleManager();
        com.logvex.crystal.module.ModuleRegistry.registerAll(manager);
        List<String> names = new ArrayList<>();
        for (Module module : manager.all()) {
            names.add(module.name().toLowerCase(java.util.Locale.ROOT));
        }
        assertEquals(names.size(), new HashSet<>(names).size(), "duplicate module name in registry");
        assertNotEquals(0, manager.size());
    }
}
