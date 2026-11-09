package com.logvex.crystal.commandcenter.ui;

import java.util.Locale;

/**
 * One thing the Command Center's unified search can find: a module, a setting, a command, a
 * workspace, a profile or a registered action.
 *
 * <p>A search entry is a plain value with a category, a title, a subtitle (the extra context a
 * result row shows) and a lower-cased {@code haystack} the matcher scans. Keeping the entry
 * free of live references means the index can be rebuilt cheaply and the matching itself is
 * pure and unit testable.
 */
public record SearchEntry(Category category, String title, String subtitle, String haystack,
                          String actionId, String target) {

    /** The kind of thing a result is, so the UI can group and badge results. */
    public enum Category {
        MODULE("Module", "\u2726"),
        SETTING("Setting", "\u2699"),
        COMMAND("Command", "\u2318"),
        WORKSPACE("Workspace", "\u25A6"),
        PROFILE("Profile", "\u2661"),
        ACTION("Action", "\u2692"),
        QUICK_ACTION("Quick action", "\u26A1");

        private final String label;
        private final String glyph;

        Category(String label, String glyph) {
            this.label = label;
            this.glyph = glyph;
        }

        public String label() {
            return label;
        }

        public String glyph() {
            return glyph;
        }
    }

    public SearchEntry {
        category = category == null ? Category.ACTION : category;
        title = title == null ? "" : title;
        subtitle = subtitle == null ? "" : subtitle;
        haystack = haystack == null ? "" : haystack.toLowerCase(Locale.ROOT);
        actionId = actionId == null ? "" : actionId;
        target = target == null ? "" : target;
    }

    /** A convenience factory that folds the title and subtitle into the searchable haystack. */
    public static SearchEntry of(Category category, String title, String subtitle, String actionId,
                                 String target) {
        String haystack = (title + " " + subtitle + " " + target).toLowerCase(Locale.ROOT);
        return new SearchEntry(category, title, subtitle, haystack, actionId, target);
    }
}
