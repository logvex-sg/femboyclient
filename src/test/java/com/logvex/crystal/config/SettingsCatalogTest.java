package com.logvex.crystal.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.module.ModuleManager;
import com.logvex.crystal.setting.BooleanSetting;
import org.junit.jupiter.api.Test;

/** The flat, searchable index over module settings. */
class SettingsCatalogTest {

    private static class StubModule extends Module {
        StubModule(String name) {
            super(Category.MISC, name, "");
            add("Auto Sprint", "Sprint automatically", false);
            add("Smart Jump", "Jump when needed", true);
        }
    }

    private static ModuleManager manager() {
        ModuleManager manager = new ModuleManager();
        manager.register(new StubModule("Movement"));
        manager.register(new StubModule("Combat"));
        return manager;
    }

    @Test
    void indexesEverySettingAcrossModules() {
        SettingsCatalog catalog = new SettingsCatalog(manager());
        assertEquals(4, catalog.all().size());
    }

    @Test
    void searchMatchesNameDescriptionAndModule() {
        SettingsCatalog catalog = new SettingsCatalog(manager());
        assertEquals(2, catalog.search("auto sprint").size());
        assertEquals(2, catalog.search("jump").size());
        assertEquals(2, catalog.search("movement").size());
        assertEquals(2, catalog.search("combat").size());
    }

    @Test
    void blankQueryReturnsEverything() {
        SettingsCatalog catalog = new SettingsCatalog(manager());
        assertEquals(4, catalog.search("").size());
        assertEquals(4, catalog.search(null).size());
    }

    @Test
    void modifiedListsOnlyChangedSettings() {
        SettingsCatalog catalog = new SettingsCatalog(manager());
        assertTrue(catalog.modified().isEmpty());
        flip(catalog.all().get(0).setting());
        assertEquals(1, catalog.modified().size());
        assertFalse(catalog.modified().get(0).isDefault());
    }

    @Test
    void resetModuleRestoresEverySetting() {
        SettingsCatalog catalog = new SettingsCatalog(manager());
        for (var entry : catalog.all()) {
            // Flip every boolean so nothing is default.
            flip(entry.setting());
        }
        var module = catalog.modules().byName("Movement");
        int count = catalog.resetModule(module);
        assertEquals(2, count);
        assertTrue(catalog.ofModule("Movement").stream().allMatch(SettingsCatalog.Entry::isDefault));
    }

    @Test
    void resetAllTouchesEveryModule() {
        SettingsCatalog catalog = new SettingsCatalog(manager());
        for (var entry : catalog.all()) {
            flip(entry.setting());
        }
        int count = catalog.resetAll();
        assertEquals(4, count);
        assertTrue(catalog.modified().isEmpty());
    }

    private static void flip(com.logvex.crystal.setting.Setting<?> setting) {
        BooleanSetting bool = (BooleanSetting) setting;
        bool.set(!bool.get());
    }

    @Test
    void ofModuleIsCaseInsensitive() {
        SettingsCatalog catalog = new SettingsCatalog(manager());
        assertEquals(2, catalog.ofModule("movement").size());
        assertEquals(2, catalog.ofModule("COMBAT").size());
    }
}
