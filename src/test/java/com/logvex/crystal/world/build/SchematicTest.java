package com.logvex.crystal.world.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Covers the schematic data model: bounds, materials, rotation and mirroring. */
class SchematicTest {

    private static Schematic single() {
        return Schematic.of("single", List.of(new Schematic.Block(0, 0, 0, "minecraft:stone")));
    }

    @Test
    void boundsAreDerivedAndBlocksNormalised() {
        Schematic schematic = Schematic.of("box", List.of(
                new Schematic.Block(5, 5, 5, "minecraft:stone"),
                new Schematic.Block(7, 6, 6, "minecraft:dirt")));
        assertEquals(3, schematic.sizeX());
        assertEquals(2, schematic.sizeY());
        assertEquals(2, schematic.sizeZ());
        assertEquals(2, schematic.blockCount());
        assertEquals(0, schematic.at(0, 0, 0).x());
        assertEquals("minecraft:stone", schematic.at(0, 0, 0).itemId());
        assertEquals("minecraft:dirt", schematic.at(2, 1, 1).itemId());
        assertNull(schematic.at(1, 1, 1));
    }

    @Test
    void emptySchematicHasNoExtent() {
        Schematic empty = Schematic.of("empty", List.of());
        assertEquals(0, empty.sizeX());
        assertEquals(0, empty.volume());
        assertTrue(empty.materialTotals().isEmpty());
        assertTrue(empty.palette().isEmpty());
    }

    @Test
    void materialTotalsCountAndPreserveOrder() {
        Schematic schematic = Schematic.of("mix", List.of(
                new Schematic.Block(0, 0, 0, "minecraft:stone"),
                new Schematic.Block(1, 0, 0, "minecraft:dirt"),
                new Schematic.Block(2, 0, 0, "minecraft:stone")));
        Map<String, Integer> totals = schematic.materialTotals();
        assertEquals(2, totals.get("minecraft:stone"));
        assertEquals(1, totals.get("minecraft:dirt"));
        assertEquals(List.of("minecraft:stone", "minecraft:dirt"), schematic.palette());
        assertEquals(3, schematic.volume());
    }

    @Test
    void fourRotationsReturnToTheOriginal() {
        Schematic schematic = Schematic.of("L", List.of(
                new Schematic.Block(0, 0, 0, "minecraft:stone"),
                new Schematic.Block(0, 0, 1, "minecraft:stone"),
                new Schematic.Block(0, 0, 2, "minecraft:stone")));
        Schematic rotated = schematic.rotated(4);
        assertEquals(schematic.sizeX(), rotated.sizeX());
        assertEquals(schematic.sizeZ(), rotated.sizeZ());
        assertEquals(3, rotated.blockCount());
        for (Schematic.Block block : schematic.blocks()) {
            assertTrue(rotated.at(block.x(), block.y(), block.z()) != null,
                    "rotation by 4 must be identity");
        }
    }

    @Test
    void oneRotationSwapsXAndZExtents() {
        Schematic schematic = Schematic.of("rod", List.of(
                new Schematic.Block(0, 0, 0, "minecraft:stone"),
                new Schematic.Block(0, 0, 1, "minecraft:stone"),
                new Schematic.Block(0, 0, 2, "minecraft:stone"),
                new Schematic.Block(0, 0, 3, "minecraft:stone")));
        Schematic rotated = schematic.rotated(1);
        assertEquals(4, rotated.sizeX());
        assertEquals(1, rotated.sizeZ());
        assertEquals("minecraft:stone", rotated.at(0, 0, 0).itemId());
        assertEquals("minecraft:stone", rotated.at(3, 0, 0).itemId());
    }

    @Test
    void mirroringReversesTheCorrectAxisAndIsItsOwnInverse() {
        Schematic schematic = Schematic.of("rod", List.of(
                new Schematic.Block(0, 0, 0, "minecraft:stone"),
                new Schematic.Block(1, 0, 0, "minecraft:stone"),
                new Schematic.Block(2, 0, 0, "minecraft:stone")));
        Schematic mirrored = schematic.mirroredZ();
        // The block that was at x=0 moves to x=2 and vice versa; the middle is unchanged.
        assertEquals("minecraft:stone", mirrored.at(2, 0, 0).itemId());
        assertEquals("minecraft:stone", mirrored.at(0, 0, 0).itemId());
        assertEquals("minecraft:stone", mirrored.at(1, 0, 0).itemId());
        // A mirror is its own inverse.
        Schematic twice = mirrored.mirroredZ();
        assertEquals("minecraft:stone", twice.at(0, 0, 0).itemId());
        assertEquals(3, twice.blockCount());
    }

    @Test
    void describeReportsDimensionsAndPalette() {
        assertEquals("single 1x1x1 (1 blocks, 1 types)", single().describe());
    }
}
