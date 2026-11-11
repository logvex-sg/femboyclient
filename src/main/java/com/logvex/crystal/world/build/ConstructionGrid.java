package com.logvex.crystal.world.build;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes the coordinates of a construction grid and its alignment guides around a centre.
 *
 * <p>Everything here is pure integer maths with no Minecraft dependency, so the placement of the
 * grid lines and the axis guides can be unit-tested directly. The caller decides how to draw the
 * result; this class only says <em>where</em> the lines are, bounded to the requested radius so a
 * huge radius cannot produce an unbounded list.
 */
public final class ConstructionGrid {

    /** A single grid line: a fixed axis position plus the extent it spans. */
    public record Line(boolean alongX, int fixed, int from, int to) {
    }

    /** Hard cap on the number of lines a single grid can produce, so a tiny spacing cannot blow up. */
    public static final int MAX_LINES = 512;

    private ConstructionGrid() {
    }

    /**
     * The grid lines of {@code spacing} blocks around {@code (centerX, centerZ)} within
     * {@code radius}. A spacing of zero or less yields no lines. Lines are aligned to multiples
     * of the spacing, so the grid is stable as the player moves. The result is capped at
     * {@link #MAX_LINES}.
     */
    public static List<Line> lines(int centerX, int centerZ, int radius, int spacing) {
        List<Line> result = new ArrayList<>();
        if (spacing <= 0 || radius <= 0) {
            return result;
        }
        int minX = centerX - radius;
        int maxX = centerX + radius;
        int minZ = centerZ - radius;
        int maxZ = centerZ + radius;
        int firstX = Math.floorDiv(minX, spacing) * spacing;
        for (int x = firstX; x <= maxX && result.size() < MAX_LINES; x += spacing) {
            result.add(new Line(false, x, minZ, maxZ));
        }
        int firstZ = Math.floorDiv(minZ, spacing) * spacing;
        for (int z = firstZ; z <= maxZ && result.size() < MAX_LINES; z += spacing) {
            result.add(new Line(true, z, minX, maxX));
        }
        return result;
    }

    /**
     * The two axis guide lines that pass through {@code (originX, originZ)}, spanning the radius.
     * These highlight the build's own axes so a rotated or mirrored placement stays readable.
     */
    public static List<Line> guides(int originX, int originZ, int radius) {
        List<Line> result = new ArrayList<>();
        if (radius <= 0) {
            return result;
        }
        result.add(new Line(false, originX, originZ - radius, originZ + radius));
        result.add(new Line(true, originZ, originX - radius, originX + radius));
        return result;
    }
}
