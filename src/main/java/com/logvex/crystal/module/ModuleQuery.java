package com.logvex.crystal.module;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * The pure search and ordering logic behind the ClickGUI's module browser.
 *
 * <p>It is deliberately Minecraft-independent: it works on the {@link Module} metadata the
 * registry already exposes (name, description, category, settings, dependencies, conflicts,
 * keybind), so the whole "which modules show, in what order, and why" question is unit tested
 * without a running game. {@code ClickGui} owns one and delegates its filtering to it, so the
 * draw path and the click path can never disagree about what is on screen.
 *
 * <p>Search matches the module name, its description, its category and any of its setting
 * names, so "AutoTotem" and the module that owns a "Health" setting both resolve. Ordering is
 * a small, explicit enum rather than an ad-hoc comparator so the GUI can label it.
 */
public final class ModuleQuery {

    /** How the visible module list is ordered. */
    public enum SortOrder {
        /** Registration order - the order the registry declares, grouped by feature. */
        DEFAULT("default"),
        /** Case-insensitive name order. */
        NAME("name"),
        /** Grouped by category, then by name. */
        CATEGORY("category"),
        /** Enabled modules first, then by name. */
        ENABLED_FIRST("enabled");

        private final String label;

        SortOrder(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public SortOrder next() {
            SortOrder[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private ModuleQuery() {
    }

    /** True when {@code module} matches the search needle on any indexed field. */
    public static boolean matches(Module module, String needle) {
        if (needle == null || needle.isEmpty()) {
            return true;
        }
        String key = needle.toLowerCase(Locale.ROOT);
        if (module.name().toLowerCase(Locale.ROOT).contains(key)) {
            return true;
        }
        if (module.description().toLowerCase(Locale.ROOT).contains(key)) {
            return true;
        }
        if (module.category().displayName().toLowerCase(Locale.ROOT).contains(key)) {
            return true;
        }
        return matchesSetting(module, key);
    }

    /** True when any setting of the module matches the needle (used for search and highlighting). */
    public static boolean matchesSetting(Module module, String needle) {
        if (needle == null || needle.isEmpty()) {
            return false;
        }
        for (var group : module.groups()) {
            for (var setting : group.settings()) {
                if (setting.name().toLowerCase(Locale.ROOT).contains(needle)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Every setting name (across all groups) that matches the needle, for the "why it matched" hint. */
    public static List<String> matchingSettingNames(Module module, String needle) {
        List<String> result = new ArrayList<>();
        if (needle == null || needle.isEmpty()) {
            return result;
        }
        String key = needle.toLowerCase(Locale.ROOT);
        for (var group : module.groups()) {
            for (var setting : group.settings()) {
                if (setting.name().toLowerCase(Locale.ROOT).contains(key)) {
                    result.add(setting.name());
                }
            }
        }
        return result;
    }

    /**
     * Filters and orders a list of modules.
     *
     * @param all        every registered module, in registration order
     * @param search     the search box text; when non-blank it overrides {@code pageFilter} and
     *                   matches across every category
     * @param pageFilter an extra predicate for the active page (favourites, recent, category),
     *                   applied only when the search box is empty; may be {@code null}
     * @param order      the requested ordering
     * @param recent     the recent-modules list, most recent first; used to order the recent page
     *                   and to break ties. May be empty, never {@code null}
     */
    public static List<Module> apply(List<Module> all, String search, Predicate<Module> pageFilter,
                                     SortOrder order, List<String> recent) {
        String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<Module> result = new ArrayList<>();
        for (Module module : all) {
            if (!needle.isEmpty()) {
                if (matches(module, needle)) {
                    result.add(module);
                }
            } else if (pageFilter == null || pageFilter.test(module)) {
                result.add(module);
            }
        }
        result.sort(comparator(order, recent));
        return result;
    }

    /** The comparator for an ordering; stable so equal keys keep registration order. */
    public static Comparator<Module> comparator(SortOrder order, List<String> recent) {
        return switch (order) {
            case DEFAULT -> (a, b) -> 0;
            case NAME -> Comparator.comparing(Module::name, String.CASE_INSENSITIVE_ORDER);
            case CATEGORY -> Comparator.comparing((Module m) -> m.category().ordinal())
                    .thenComparing(Module::name, String.CASE_INSENSITIVE_ORDER);
            case ENABLED_FIRST -> Comparator.comparing((Module m) -> m.isEnabled() ? 0 : 1)
                    .thenComparing(Module::name, String.CASE_INSENSITIVE_ORDER);
        };
    }

    /** A short human reason a module matched, e.g. {@code "setting: Health"} or {@code "name"}. */
    public static String matchReason(Module module, String search) {
        String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return "";
        }
        if (module.name().toLowerCase(Locale.ROOT).contains(needle)) {
            return "name";
        }
        if (module.description().toLowerCase(Locale.ROOT).contains(needle)) {
            return "description";
        }
        if (module.category().displayName().toLowerCase(Locale.ROOT).contains(needle)) {
            return "category";
        }
        List<String> settings = matchingSettingNames(module, needle);
        return settings.isEmpty() ? "" : "setting: " + String.join(", ", settings);
    }

    /** One line describing a module's dependency/conflict state for the card indicator tooltip. */
    public static String requirementLine(Module module, ModuleManager manager) {
        if (manager == null) {
            return "";
        }
        String missing = manager.firstMissingDependency(module);
        if (missing != null) {
            return "needs " + missing;
        }
        Module conflict = manager.firstActiveConflict(module);
        if (conflict != null) {
            return "conflicts with " + conflict.name();
        }
        if (!module.dependencies().isEmpty() || !module.conflicts().isEmpty()) {
            return "requirements satisfied";
        }
        return "";
    }
}
