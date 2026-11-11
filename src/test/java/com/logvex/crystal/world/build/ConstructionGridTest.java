package com.logvex.crystal.world.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConstructionGridTest {

    @Test
    void noLinesForNonPositiveSpacingOrRadius() {
        assertTrue(ConstructionGrid.lines(0, 0, 32, 0).isEmpty());
        assertTrue(ConstructionGrid.lines(0, 0, 32, -4).isEmpty());
        assertTrue(ConstructionGrid.lines(0, 0, 0, 8).isEmpty());
        assertTrue(ConstructionGrid.guides(0, 0, 0).isEmpty());
    }

    @Test
    void linesAreAlignedToSpacingMultiples() {
        List<ConstructionGrid.Line> lines = ConstructionGrid.lines(5, 5, 10, 8);
        for (ConstructionGrid.Line line : lines) {
            assertEquals(0, Math.floorMod(line.fixed(), 8), "line must sit on a spacing multiple");
        }
    }

    @Test
    void linesSpanTheRadius() {
        List<ConstructionGrid.Line> lines = ConstructionGrid.lines(0, 0, 16, 16);
        // Multiples of 16 within [-16,16]: -16, 0, 16 on each axis.
        assertEquals(6, lines.size());
        boolean hasAxisX = lines.stream().anyMatch(l -> !l.alongX() && l.fixed() == 0
                && l.from() == -16 && l.to() == 16);
        boolean hasAxisZ = lines.stream().anyMatch(l -> l.alongX() && l.fixed() == 0
                && l.from() == -16 && l.to() == 16);
        assertTrue(hasAxisX, "an X-axis grid line at 0 spanning the radius");
        assertTrue(hasAxisZ, "a Z-axis grid line at 0 spanning the radius");
    }

    @Test
    void lineCountIsCapped() {
        // radius 128, spacing 1 would be 514 lines; the cap keeps it bounded.
        List<ConstructionGrid.Line> lines = ConstructionGrid.lines(0, 0, 128, 1);
        assertTrue(lines.size() <= ConstructionGrid.MAX_LINES);
    }

    @Test
    void guidesPassThroughTheOrigin() {
        List<ConstructionGrid.Line> guides = ConstructionGrid.guides(100, -50, 20);
        assertEquals(2, guides.size());
        assertTrue(guides.stream().anyMatch(l -> !l.alongX() && l.fixed() == 100));
        assertTrue(guides.stream().anyMatch(l -> l.alongX() && l.fixed() == -50));
        for (ConstructionGrid.Line guide : guides) {
            assertEquals(40, guide.to() - guide.from());
        }
    }
}
