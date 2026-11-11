package com.logvex.crystal.config;

import com.logvex.crystal.module.Module;
import com.logvex.crystal.module.ModuleManager;
import com.logvex.crystal.setting.Setting;
import com.logvex.crystal.setting.SettingGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A flat, searchable index over every module setting.
 *
 * <p>Module settings already live in a two-level structure (module → group → setting), which
 * is right for the ClickGUI but awkward for "find the setting that controls X". This builds a
 * single list of {@link Entry} records - module name, group name, setting, current value,
 * whether it differs from its default - and filters it by a free-text query against the
 * setting name, its description and the module name.
 *
 * <p>It holds no state of its own beyond the manager reference, so it always reflects the
 * live configuration. Rebuilding the index is cheap (a few hundred settings) and is done on
 * demand rather than cached, so there is nothing to invalidate.
 */
public final class SettingsCatalog {

    /** One setting plus the context needed to display and reset it. */
    public record Entry(String module, String group, Setting<?> setting) {
        public String label() {
            return module + " \u00b7 " + setting.name();
        }

        public boolean isDefault() {
            return setting.isDefault();
        }
    }

    private final ModuleManager modules;

    public SettingsCatalog(ModuleManager modules) {
        this.modules = modules;
    }

    /** Every setting across every registered module, in registration order. */
    public List<Entry> all() {
        List<Entry> result = new ArrayList<>();
        for (Module module : modules.all()) {
            for (SettingGroup group : module.groups()) {
                for (Setting<?> setting : group.settings()) {
                    result.add(new Entry(module.name(), group.name(), setting));
                }
            }
        }
        return result;
    }

    /**
     * Settings matching a free-text query. A blank query returns everything. Matching is
     * case-insensitive over the setting name, its description and the module name.
     */
    public List<Entry> search(String query) {
        List<Entry> all = all();
        if (query == null || query.isBlank()) {
            return all;
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<Entry> result = new ArrayList<>();
        for (Entry entry : all) {
            if (entry.setting().name().toLowerCase(Locale.ROOT).contains(needle)
                    || entry.setting().description().toLowerCase(Locale.ROOT).contains(needle)
                    || entry.module().toLowerCase(Locale.ROOT).contains(needle)) {
                result.add(entry);
            }
        }
        return result;
    }

    /** Settings that differ from their default, so the UI can show what has been changed. */
    public List<Entry> modified() {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : all()) {
            if (!entry.isDefault()) {
                result.add(entry);
            }
        }
        return result;
    }

    /** Every setting of one module (by name), case-insensitively. */
    public List<Entry> ofModule(String moduleName) {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : all()) {
            if (entry.module().equalsIgnoreCase(moduleName)) {
                result.add(entry);
            }
        }
        return result;
    }

    /** Resets one setting to its default. Returns true when a value was found. */
    public boolean reset(Module module, Setting<?> setting) {
        if (module == null || setting == null) {
            return false;
        }
        setting.reset();
        return true;
    }

    /** Resets every setting of a module. Returns the number reset. */
    public int resetModule(Module module) {
        if (module == null) {
            return 0;
        }
        int count = 0;
        for (SettingGroup group : module.groups()) {
            for (Setting<?> setting : group.settings()) {
                if (!setting.isDefault()) {
                    setting.reset();
                }
                count++;
            }
        }
        return count;
    }

    /** Resets every setting across every module. Returns the number of settings touched. */
    public int resetAll() {
        int count = 0;
        for (Module module : modules.all()) {
            count += resetModule(module);
        }
        return count;
    }

    public ModuleManager modules() {
        return modules;
    }
}
