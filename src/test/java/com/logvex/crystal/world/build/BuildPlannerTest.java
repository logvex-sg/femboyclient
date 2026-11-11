package com.logvex.crystal.world.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Covers the build material planner: deficits, readiness and the checklist. */
class BuildPlannerTest {

    private static Schematic schematic() {
        return Schematic.of("wall", List.of(
                new Schematic.Block(0, 0, 0, "minecraft:stone"),
                new Schematic.Block(1, 0, 0, "minecraft:stone"),
                new Schematic.Block(2, 0, 0, "minecraft:glass"),
                new Schematic.Block(3, 0, 0, "minecraft:glass")));
    }

    @Test
    void missingIsThePerItemDeficitOnly() {
        Map<String, Integer> missing = BuildPlanner.missing(schematic(),
                id -> id.equals("minecraft:stone") ? 1 : 0);
        assertEquals(1, missing.get("minecraft:stone"));
        assertEquals(2, missing.get("minecraft:glass"));
        assertFalse(missing.containsKey("minecraft:air"));
    }

    @Test
    void readinessIsTheSatisfiedFraction() {
        // One of each item held, two of each required -> half the blocks are covered.
        assertEquals(0.5, BuildPlanner.readiness(schematic(), id -> 1), 1e-9);
        // Over-supply is clamped to 1.
        assertEquals(1.0, BuildPlanner.readiness(schematic(), id -> 999), 1e-9);
        assertEquals(1.0, BuildPlanner.readiness(Schematic.of("empty", List.of()), id -> 0), 1e-9);
    }

    @Test
    void stacksMissingRoundsUpAcrossItems() {
        // 1 stone (1 stack) + 2 glass (1 stack) = 2 stacks.
        assertEquals(2, BuildPlanner.stacksMissing(schematic(), id -> 0));
    }

    @Test
    void checklistReportsHaveTargetAndMissingPerLine() {
        List<BuildPlanner.MaterialLine> lines = BuildPlanner.checklist(schematic(),
                id -> id.equals("minecraft:stone") ? 5 : 1);
        assertEquals(2, lines.size());
        BuildPlanner.MaterialLine stone = lines.get(0);
        assertEquals("minecraft:stone", stone.itemId());
        // Held is clamped to the target in the readout, and the deficit is zero.
        assertEquals(2, stone.have());
        assertEquals(2, stone.target());
        assertTrue(stone.complete());
        BuildPlanner.MaterialLine glass = lines.get(1);
        assertEquals(1, glass.have());
        assertEquals(2, glass.target());
        assertEquals(1, glass.missing());
        assertFalse(glass.complete());
    }
}
