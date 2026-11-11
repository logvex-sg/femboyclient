package com.logvex.crystal.gui.hud;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure helpers for the keybind-list HUD element.
 *
 * <p>Kept free of Minecraft types so the strings a readout will show can be unit-tested
 * without a client: the element feeds in {@code name -> bound key} pairs and gets back the
 * formatted, ordered lines.
 */
public final class KeybindReadouts {

    /** One module's bound key. */
    public record Entry(String module, String key) {
    }

    private KeybindReadouts() {
    }

    /**
     * Formats bound modules as {@code "name  key"} lines, sorted by module name.
     *
     * <p>Unbound entries and blank names are dropped, so the list only ever shows real
     * bindings. The key column is aligned to the widest name.
     */
    public static List<String> lines(List<Entry> entries) {
        List<Entry> bound = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry == null || entry.module() == null || entry.module().isBlank()) {
                continue;
            }
            if (entry.key() == null || entry.key().isBlank() || "none".equalsIgnoreCase(entry.key())) {
                continue;
            }
            bound.add(entry);
        }
        bound.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.module(), b.module()));
        int width = 0;
        for (Entry entry : bound) {
            width = Math.max(width, entry.module().length());
        }
        List<String> lines = new ArrayList<>(bound.size());
        for (Entry entry : bound) {
            lines.add(pad(entry.module(), width) + "  " + entry.key());
        }
        return lines;
    }

    private static String pad(String value, int width) {
        if (value.length() >= width) {
            return value;
        }
        return value + " ".repeat(width - value.length());
    }
}
