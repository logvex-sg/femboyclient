package com.logvex.crystal.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Covers registration, dependency/conflict resolution and keybind dispatch. Uses small
 * stub modules so no Minecraft runtime is needed; the real registry count is asserted
 * separately by {@link ModuleRegistryTest}.
 */
class ModuleManagerTest {

    /** A minimal module that records lifecycle calls. */
    private static class StubModule extends Module {
        int enables;
        int disables;
        int ticks;

        StubModule(Category category, String name) {
            super(category, name, "");
        }

        @Override
        protected void onEnable() {
            enables++;
        }

        @Override
        protected void onDisable() {
            disables++;
        }

        @Override
        protected void onTick() {
            ticks++;
        }
    }

    @Test
    void registersAndLooksUpByName() {
        ModuleManager manager = new ModuleManager();
        StubModule a = new StubModule(Category.COMBAT, "Alpha");
        manager.register(a);
        assertEquals(1, manager.size());
        assertEquals(a, manager.byName("alpha"), "lookup is case-insensitive");
        assertEquals(a, manager.byName("ALPHA"));
        assertNull(manager.byName("missing"));
        assertEquals(a, manager.byClass(StubModule.class));
    }

    @Test
    void duplicateNamesAreRejected() {
        ModuleManager manager = new ModuleManager();
        manager.register(new StubModule(Category.COMBAT, "Dup"));
        assertThrows(IllegalStateException.class,
                () -> manager.register(new StubModule(Category.MOVEMENT, "dup")));
    }

    @Test
    void enableRespectsDependencies() {
        ModuleManager manager = new ModuleManager();
        StubModule dep = new StubModule(Category.COMBAT, "Dep");
        // Declare the dependency through a subclass that can call the protected helper.
        DependentModule child = new DependentModule(dep);
        manager.register(dep);
        manager.register(child);
        assertFalse(child.enable(manager), "dependency is disabled");
        assertTrue(child.lastError().contains("Dep"));
        dep.enable(manager);
        assertTrue(child.enable(manager));
        assertEquals(1, child.enables);
    }

    private static final class DependentModule extends StubModule {
        DependentModule(Module dependency) {
            super(Category.COMBAT, "Dependent");
            dependsOn(dependency.name());
        }
    }

    @Test
    void enableRespectsConflicts() {
        ModuleManager manager = new ModuleManager();
        StubModule first = new StubModule(Category.MOVEMENT, "First");
        ConflictingModule second = new ConflictingModule();
        manager.register(first);
        manager.register(second);
        first.enable(manager);
        assertFalse(second.enable(manager), "conflicting module must not enable");
        assertTrue(second.lastError().contains("First"));
    }

    private static final class ConflictingModule extends StubModule {
        ConflictingModule() {
            super(Category.MOVEMENT, "Second");
            conflictsWith("First");
        }
    }

    @Test
    void tickOnlyRunsEnabledModules() {
        ModuleManager manager = new ModuleManager();
        StubModule module = new StubModule(Category.MISC, "Ticked");
        manager.register(module);
        manager.tickAll();
        assertEquals(0, module.ticks);
        module.enable(manager);
        manager.tickAll();
        assertEquals(1, module.ticks);
    }

    @Test
    void alwaysEnabledModulesCannotBeDisabled() {
        ModuleManager manager = new ModuleManager();
        AlwaysModule module = new AlwaysModule();
        manager.register(module);
        assertTrue(module.isEnabled());
        module.disable();
        assertTrue(module.isEnabled(), "always-enabled modules ignore disable");
    }

    private static final class AlwaysModule extends StubModule {
        AlwaysModule() {
            super(Category.MISC, "Always");
            alwaysEnabled();
        }
    }

    @Test
    void applyEnabledStatesResolvesOutOfOrderDependencies() {
        ModuleManager manager = new ModuleManager();
        StubModule dep = new StubModule(Category.COMBAT, "Base");
        DependentModule child = new DependentModule(dep);
        manager.register(dep);
        manager.register(child);
        Map<String, Boolean> states = new LinkedHashMap<>();
        // The dependent is listed first, so a single pass would fail.
        states.put("Dependent", true);
        states.put("Base", true);
        manager.applyEnabledStates(states);
        assertTrue(dep.isEnabled());
        assertTrue(child.isEnabled(), "retry passes must resolve the dependency");
    }

    @Test
    void keybindTogglesBoundModule() {
        ModuleManager manager = new ModuleManager();
        StubModule module = new StubModule(Category.MISC, "Bound");
        manager.register(module);
        module.keybind().set(42);
        manager.onKeyPressed(42);
        assertTrue(module.isEnabled());
        manager.onKeyPressed(42);
        assertFalse(module.isEnabled());
        // Unbound modules never respond.
        manager.onKeyPressed(-1);
        assertFalse(module.isEnabled());
    }

    @Test
    void disconnectDisablesModules() {
        ModuleManager manager = new ModuleManager();
        StubModule module = new StubModule(Category.MISC, "World");
        manager.register(module);
        module.enable(manager);
        manager.onDisconnect();
        assertFalse(module.isEnabled());
        assertEquals(1, module.disables);
    }

    @Test
    void byCategoryFilters() {
        ModuleManager manager = new ModuleManager();
        manager.register(new StubModule(Category.COMBAT, "C1"));
        manager.register(new StubModule(Category.MOVEMENT, "M1"));
        manager.register(new StubModule(Category.COMBAT, "C2"));
        assertEquals(2, manager.byCategory(Category.COMBAT).size());
        assertEquals(1, manager.byCategory(Category.MOVEMENT).size());
        assertEquals(0, manager.enabledCount());
    }

    @Test
    void boundToListsEveryHolderOfAKey() {
        ModuleManager manager = new ModuleManager();
        StubModule a = new StubModule(Category.MISC, "A");
        StubModule b = new StubModule(Category.MISC, "B");
        StubModule c = new StubModule(Category.MISC, "C");
        manager.register(a);
        manager.register(b);
        manager.register(c);
        a.keybind().set(10);
        b.keybind().set(10);
        c.keybind().set(20);

        assertEquals(2, manager.boundTo(10).size());
        assertEquals(1, manager.boundTo(20).size());
        assertTrue(manager.boundTo(-1).isEmpty(), "unbound key matches nobody");
        assertTrue(manager.boundTo(99).isEmpty());
    }

    @Test
    void conflictsOfExcludesTheModuleItself() {
        ModuleManager manager = new ModuleManager();
        StubModule a = new StubModule(Category.MISC, "A");
        StubModule b = new StubModule(Category.MISC, "B");
        manager.register(a);
        manager.register(b);
        a.keybind().set(10);
        b.keybind().set(10);

        assertEquals(1, manager.conflictsOf(a).size());
        assertEquals(b, manager.conflictsOf(a).get(0));
        assertTrue(manager.hasKeybindConflicts());

        b.keybind().unbind();
        assertTrue(manager.conflictsOf(a).isEmpty());
        assertFalse(manager.hasKeybindConflicts());
    }

    @Test
    void resolveKeybindConflictUnbindsOtherHolders() {
        ModuleManager manager = new ModuleManager();
        StubModule a = new StubModule(Category.MISC, "A");
        StubModule b = new StubModule(Category.MISC, "B");
        manager.register(a);
        manager.register(b);
        a.keybind().set(10);
        b.keybind().set(10);

        int cleared = manager.resolveKeybindConflict(b, 10);
        assertEquals(1, cleared);
        assertFalse(a.keybind().isBound(), "the other holder loses the key");
        assertFalse(manager.hasKeybindConflicts());
    }

    @Test
    void resolveKeybindConflictOnUnboundKeyIsANoOp() {
        ModuleManager manager = new ModuleManager();
        StubModule a = new StubModule(Category.MISC, "A");
        manager.register(a);
        a.keybind().set(10);
        assertEquals(0, manager.resolveKeybindConflict(a, -1));
        assertTrue(a.keybind().isBound(), "clearing with an unbound key must not touch bindings");
    }

    // ---------------------------------------------------- lifecycle cleanup

    /** A module that acquires a resource on enable and releases it on disable. */
    private static class ResourceModule extends StubModule {
        boolean holding;
        int acquisitions;

        ResourceModule(String name) {
            super(Category.MISC, name);
        }

        @Override
        protected void onEnable() {
            super.onEnable();
            holding = true;
            acquisitions++;
        }

        @Override
        protected void onDisable() {
            super.onDisable();
            holding = false;
        }
    }

    @Test
    void disablingAModuleReleasesItsResources() {
        ModuleManager manager = new ModuleManager();
        ResourceModule module = new ResourceModule("Holder");
        manager.register(module);
        module.enable(manager);
        assertTrue(module.holding, "enable acquires the resource");
        module.disable();
        assertFalse(module.holding, "disable must release every resource the module held");
        assertEquals(1, module.disables);
    }

    @Test
    void aFailedEnableUnwindsInsteadOfLeaking() {
        ModuleManager manager = new ModuleManager();
        ResourceModule module = new ResourceModule("Leaky") {
            @Override
            protected void onEnable() {
                super.onEnable();
                throw new IllegalStateException("boom");
            }
        };
        manager.register(module);
        assertFalse(module.enable(manager), "a throwing onEnable must report failure");
        assertFalse(module.holding, "resources acquired before the throw must be released");
        assertFalse(module.isEnabled(), "a failed enable leaves the module disabled");
        assertTrue(module.errorCount() >= 1, "the failure is recorded");
    }

    @Test
    void disconnectRunsCleanupForEveryEnabledModule() {
        ModuleManager manager = new ModuleManager();
        ResourceModule a = new ResourceModule("A");
        ResourceModule b = new ResourceModule("B");
        manager.register(a);
        manager.register(b);
        a.enable(manager);
        b.enable(manager);
        manager.onDisconnect();
        assertFalse(a.holding);
        assertFalse(b.holding);
    }

    @Test
    void shutdownRunsForEveryModuleEvenWhenDisabled() {
        ModuleManager manager = new ModuleManager();
        final int[] shutdowns = {0};
        StubModule module = new StubModule(Category.MISC, "Worker") {
            @Override
            protected void onShutdown() {
                shutdowns[0]++;
            }
        };
        manager.register(module);
        manager.shutdownAll();
        assertEquals(1, shutdowns[0], "shutdown must reach a module that was never enabled");
    }
}
