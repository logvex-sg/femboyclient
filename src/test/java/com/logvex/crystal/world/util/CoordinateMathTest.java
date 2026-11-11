package com.logvex.crystal.world.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Covers the coordinate, distance and bearing arithmetic used by the world utilities. */
class CoordinateMathTest {

    @Test
    void chunkMappingFloorsAcrossTheOrigin() {
        assertEquals(0, CoordinateMath.chunkOf(0));
        assertEquals(0, CoordinateMath.chunkOf(15));
        assertEquals(1, CoordinateMath.chunkOf(16));
        // Negative coordinates must floor, not truncate toward zero.
        assertEquals(-1, CoordinateMath.chunkOf(-1));
        assertEquals(-1, CoordinateMath.chunkOf(-16));
        assertEquals(-2, CoordinateMath.chunkOf(-17));
    }

    @Test
    void chunkOriginIsTheFirstBlockOfTheChunk() {
        assertEquals(0, CoordinateMath.chunkOrigin(5));
        assertEquals(16, CoordinateMath.chunkOrigin(16));
        assertEquals(-16, CoordinateMath.chunkOrigin(-1));
    }

    @Test
    void regionMappingFloorsEvery512Blocks() {
        assertEquals(0, CoordinateMath.regionOf(0));
        assertEquals(0, CoordinateMath.regionOf(511));
        assertEquals(1, CoordinateMath.regionOf(512));
        assertEquals(-1, CoordinateMath.regionOf(-1));
        assertEquals(-1, CoordinateMath.regionOf(-512));
        assertEquals(-2, CoordinateMath.regionOf(-513));
    }

    @Test
    void distancesAreEuclidean() {
        assertEquals(3.0, CoordinateMath.distance(0, 0, 0, 3, 0, 0), 1e-9);
        assertEquals(5.0, CoordinateMath.distance(0, 0, 0, 3, 4, 0), 1e-9);
        assertEquals(5.0, CoordinateMath.horizontalDistance(0, 0, 3, 4), 1e-9);
        // Horizontal distance ignores Y entirely.
        assertEquals(1.0, CoordinateMath.horizontalDistance(0, 0, 0, 1), 1e-9);
    }

    @Test
    void compassPointsCoverTheEightDirections() {
        assertEquals("south", CoordinateMath.compassPoint(0));
        assertEquals("west", CoordinateMath.compassPoint(90));
        assertEquals("north", CoordinateMath.compassPoint(180));
        assertEquals("east", CoordinateMath.compassPoint(270));
        assertEquals("north-east", CoordinateMath.compassPoint(225));
        // A negative yaw wraps the same way as a large positive one.
        assertEquals(CoordinateMath.compassPoint(350), CoordinateMath.compassPoint(-10));
    }

    @Test
    void netherConversionRoundsSymmetrically() {
        assertEquals(16, CoordinateMath.toNether(128));
        assertEquals(2, CoordinateMath.toNether(12));
        assertEquals(-2, CoordinateMath.toNether(-12));
        assertEquals(128, CoordinateMath.toOverworld(16));
    }

    @Test
    void formatIncludesChunkCoordinates() {
        assertEquals("123, 64, -45 (7, -3)", CoordinateMath.format(123, 64, -45));
    }
}
