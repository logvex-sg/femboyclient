package com.logvex.crystal.gui;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-element placement: whether it is visible, which column it lives in, an explicit
 * offset (used when the user drags it), and an opacity multiplier. Persisted alongside
 * the module config.
 *
 * <p>An element with {@code customPosition == false} auto-stacks in its column; once the
 * user drags it the flag flips and the explicit offsets win, so a freshly installed
 * client still lays out sensibly without any stored positions.
 */
public final class HudLayout {

    /** Placement of one element. */
    public static final class Entry {
        public boolean visible = true;
        public boolean customPosition;
        public int x;
        public int y;
        public float opacity = 1.0f;
        public float scale = 1.0f;
        /** Draw a translucent rounded panel behind the element. */
        public boolean background;
        /** Draw a soft outline around the element. */
        public boolean border;
        /** Draw order; lower layers draw first (behind), higher layers on top. */
        public int layer;
        /** Explicit width override in pixels; 0 means "use the element's own width". */
        public int width;
        /** Explicit height override in pixels; 0 means "use the element's own height". */
        public int height;

        public Entry copy() {
            Entry e = new Entry();
            e.visible = visible;
            e.customPosition = customPosition;
            e.x = x;
            e.y = y;
            e.opacity = opacity;
            e.scale = scale;
            e.background = background;
            e.border = border;
            e.layer = layer;
            e.width = width;
            e.height = height;
            return e;
        }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public Entry entry(String id) {
        return entries.computeIfAbsent(id, k -> new Entry());
    }

    public boolean has(String id) {
        return entries.containsKey(id);
    }

    public Map<String, Entry> all() {
        return entries;
    }

    public void clear() {
        entries.clear();
    }
}
