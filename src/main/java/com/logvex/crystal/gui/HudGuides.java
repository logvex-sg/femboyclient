package com.logvex.crystal.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Alignment guides for the HUD layout editor: given the rectangle being dragged and every
 * other element's rectangle, it reports the screen lines the dragged element is (nearly)
 * aligned to, so the editor can draw a guide and snap the element onto it.
 *
 * <p>Pure geometry, unit tested without a client. Guides are returned as absolute screen
 * coordinates in two lists (vertical lines at an x, horizontal lines at a y).
 */
public final class HudGuides {

    /** The guide lines the dragged rectangle aligns to. */
    public record Result(List<Integer> vertical, List<Integer> horizontal, Integer snapX, Integer snapY) {
        public static final Result EMPTY = new Result(List.of(), List.of(), null, null);
    }

    private HudGuides() {
    }

    /**
     * Finds alignment guides between {@code moving} and every rectangle in {@code others}.
     *
     * @param threshold how close (in pixels) an edge must be before it counts as aligned
     * @return the guide lines plus the snapped x/y the editor should apply, or nulls when
     *         nothing is close enough
     */
    public static Result find(HudSnap.Rect moving, Map<String, HudSnap.Rect> others, int threshold) {
        List<Integer> vertical = new ArrayList<>();
        List<Integer> horizontal = new ArrayList<>();
        Integer snapX = null;
        Integer snapY = null;
        int bestX = threshold + 1;
        int bestY = threshold + 1;

        int[] movingX = {moving.x(), moving.x() + moving.width() / 2, moving.x() + moving.width()};
        int[] movingY = {moving.y(), moving.y() + moving.height() / 2, moving.y() + moving.height()};

        for (HudSnap.Rect other : others.values()) {
            int[] otherX = {other.x(), other.x() + other.width() / 2, other.x() + other.width()};
            int[] otherY = {other.y(), other.y() + other.height() / 2, other.y() + other.height()};
            for (int mx : movingX) {
                for (int ox : otherX) {
                    int distance = Math.abs(mx - ox);
                    if (distance <= threshold) {
                        if (!vertical.contains(ox)) {
                            vertical.add(ox);
                        }
                        if (distance < bestX) {
                            bestX = distance;
                            // Snap so the matched edge of the moving rect lands on the guide.
                            snapX = moving.x() + (ox - mx);
                        }
                    }
                }
            }
            for (int my : movingY) {
                for (int oy : otherY) {
                    int distance = Math.abs(my - oy);
                    if (distance <= threshold) {
                        if (!horizontal.contains(oy)) {
                            horizontal.add(oy);
                        }
                        if (distance < bestY) {
                            bestY = distance;
                            snapY = moving.y() + (oy - my);
                        }
                    }
                }
            }
        }
        return new Result(vertical, horizontal, snapX, snapY);
    }
}
