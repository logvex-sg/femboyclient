package com.logvex.crystal.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Covers the ClickGUI's search and ordering engine with stub modules, so the behaviour the
 * browser relies on - which modules show, in what order, and why - is verified without a game.
 */
class ModuleQueryTest {

    private static class Stub extends Module {
        Stub(Category category, String name, String description) {
            super(category, name, description);
        }
    }

    private static Stub module(Category category, String name, String description) {
        return new Stub(category, name, description);
    }

    @Test
    void blankSearchMatchesEverything() {
        Stub a = module(Category.COMBAT, "AutoCrystal", "places crystals");
        assertTrue(ModuleQuery.matches(a, ""));
        assertTrue(ModuleQuery.matches(a, null));
    }

    @Test
    void searchMatchesNameDescriptionCategoryAndSetting() {
        Stub a = module(Category.COMBAT, "AutoCrystal", "places crystals against a target");
        assertTrue(ModuleQuery.matches(a, "crystal"));
        assertTrue(ModuleQuery.matches(a, "target"));
        assertTrue(ModuleQuery.matches(a, "combat"));
        assertFalse(ModuleQuery.matches(a, "totem"));
    }

    @Test
    void searchMatchesSettingNames() {
        Stub a = module(Category.RENDER, "EntityEsp", "outlines entities");
        a.add("Health", "Show health", true);
        assertTrue(ModuleQuery.matches(a, "health"));
        assertEquals(List.of("Health"), ModuleQuery.matchingSettingNames(a, "health"));
        assertEquals("setting: Health", ModuleQuery.matchReason(a, "health"));
    }

    @Test
    void matchReasonReportsTheStrongestField() {
        Stub a = module(Category.COMBAT, "AutoCrystal", "places crystals");
        assertEquals("name", ModuleQuery.matchReason(a, "crystal"));
        assertEquals("description", ModuleQuery.matchReason(a, "places"));
        assertEquals("category", ModuleQuery.matchReason(a, "combat"));
        assertEquals("", ModuleQuery.matchReason(a, ""));
    }

    @Test
    void defaultOrderPreservesRegistrationOrder() {
        Stub a = module(Category.COMBAT, "Zeta", "");
        Stub b = module(Category.COMBAT, "Alpha", "");
        List<Module> result = ModuleQuery.apply(List.of(a, b), "", null, ModuleQuery.SortOrder.DEFAULT, List.of());
        assertEquals(List.of(a, b), result);
    }

    @Test
    void nameOrderSortsCaseInsensitively() {
        Stub zeta = module(Category.COMBAT, "zeta", "");
        Stub alpha = module(Category.COMBAT, "Alpha", "");
        List<Module> result = ModuleQuery.apply(List.of(zeta, alpha), "", null, ModuleQuery.SortOrder.NAME, List.of());
        assertEquals(List.of(alpha, zeta), result);
    }

    @Test
    void categoryOrderGroupsThenSortsByName() {
        Stub combatZ = module(Category.COMBAT, "Zeta", "");
        Stub renderA = module(Category.RENDER, "Alpha", "");
        Stub combatA = module(Category.COMBAT, "Alpha", "");
        List<Module> result = ModuleQuery.apply(List.of(combatZ, renderA, combatA), "",
                null, ModuleQuery.SortOrder.CATEGORY, List.of());
        assertEquals(List.of(combatA, combatZ, renderA), result);
    }

    @Test
    void enabledFirstPutsActiveModulesOnTop() {
        Stub off = module(Category.COMBAT, "Alpha", "");
        Stub on = module(Category.COMBAT, "Beta", "");
        ModuleManager manager = new ModuleManager();
        manager.register(off);
        manager.register(on);
        on.enable(manager);
        List<Module> result = ModuleQuery.apply(List.of(off, on), "", null,
                ModuleQuery.SortOrder.ENABLED_FIRST, List.of());
        assertEquals(List.of(on, off), result);
    }

    @Test
    void pageFilterAppliesOnlyWhenSearchIsEmpty() {
        Stub combat = module(Category.COMBAT, "Alpha", "");
        Stub render = module(Category.RENDER, "Beta", "");
        List<Module> filtered = ModuleQuery.apply(List.of(combat, render), "",
                m -> m.category() == Category.COMBAT, ModuleQuery.SortOrder.DEFAULT, List.of());
        assertEquals(List.of(combat), filtered);
        // A non-blank search overrides the page filter and searches every category.
        List<Module> searched = ModuleQuery.apply(List.of(combat, render), "beta",
                m -> m.category() == Category.COMBAT, ModuleQuery.SortOrder.DEFAULT, List.of());
        assertEquals(List.of(render), searched);
    }

    @Test
    void sortOrderCyclesThroughEveryValue() {
        ModuleQuery.SortOrder order = ModuleQuery.SortOrder.DEFAULT;
        for (int i = 0; i < ModuleQuery.SortOrder.values().length; i++) {
            order = order.next();
        }
        assertSame(ModuleQuery.SortOrder.DEFAULT, order);
    }

    @Test
    void requirementLineReportsMissingDependencyAndConflict() {
        Stub base = module(Category.COMBAT, "Base", "");
        Stub dependent = module(Category.COMBAT, "Dependent", "");
        ModuleManager manager = new ModuleManager();
        manager.register(base);
        manager.register(dependent);
        dependent.dependsOn("Base");
        assertEquals("needs Base (disabled)", ModuleQuery.requirementLine(dependent, manager));

        base.enable(manager);
        assertEquals("requirements satisfied", ModuleQuery.requirementLine(dependent, manager));

        Stub conflict = module(Category.COMBAT, "Conflict", "");
        manager.register(conflict);
        conflict.conflictsWith("Base");
        conflict.enable(manager);
        assertEquals("conflicts with Base", ModuleQuery.requirementLine(conflict, manager));
    }

    @Test
    void requirementLineIsBlankForIndependentModules() {
        Stub a = module(Category.COMBAT, "Alpha", "");
        ModuleManager manager = new ModuleManager();
        manager.register(a);
        assertEquals("", ModuleQuery.requirementLine(a, manager));
    }
}
