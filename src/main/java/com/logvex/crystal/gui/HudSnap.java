package com.logvex.crystal.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure geometry helpers for the HUD layout editor: snapping a dragged element to a grid,
 * detecting elements that overlap (so two panels cannot silently sit on top of each other),
 * and clamping an element back onto the screen.
 *
 * <p>Kept free of Minecraft types so the placement rules are unit tested without a client.
 */
public final class HudSnap {

    /** An axis-aligned rectangle in screen pixels. */
    public record Rect(int x, int y, int width, int height) {
        public boolean contains(int px, int py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    private HudSnap() {
    }

    /** Rounds a coordinate to the nearest multiple of {@code grid}; a grid of 1 is a no-op. */
    public static int snap(int value, int grid) {
        if (grid <= 1) {
            return value;
        }
        return Math.round((float) value / grid) * grid;
    }

    /** Keeps an element fully on screen. */
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** True when two rectangles overlap (touching edges do not count). */
    public static boolean overlaps(Rect a, Rect b) {
        return a.x() < b.x() + b.width() && b.x() < a.x() + a.width()
                && a.y() < b.y() + b.height() && b.y() < a.y() + a.height();
    }

    /**
     * The ids of elements whose rectangles overlap another element's, mapped to one id it
     * collides with. Every id appears at most once, and an element never collides with
     * itself, so the result is safe to iterate while drawing.
     */
    public static Map<String, String> findOverlaps(Map<String, Rect> rects) {
        Map<String, String> result = new LinkedHashMap<>();
        List<Map.Entry<String, Rect>> entries = new ArrayList<>(rects.entrySet());
        for (int i = 0; i < entries.size(); i++) {
            for (int j = i + 1; j < entries.size(); j++) {
                if (overlaps(entries.get(i).getValue(), entries.get(j).getValue())) {
                    result.putIfAbsent(entries.get(i).getKey(), entries.get(j).getKey());
                    result.putIfAbsent(entries.get(j).getKey(), entries.get(i).getKey());
                }
            }
        }
        return result;
    }

    /**
     * Restores an entry to auto-stacking, clearing the explicit position and per-element
     * styling so a misplaced element returns to the default column flow.
     */
    public static void reset(HudLayout.Entry entry) {
        if (entry == null) {
            return;
        }
        entry.customPosition = false;
        entry.x = 0;
        entry.y = 0;
        entry.opacity = 1.0f;
        entry.scale = 1.0f;
        entry.background = false;
        entry.border = false;
        entry.layer = 0;
        entry.width = 0;
        entry.height = 0;
    }
}
