package com.logvex.crystal.scan.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Tests the region grid: chunk/region maths (including negatives) and region counts. */
class RegionGridTest {

    @Test
    void regionOfUsesFloorDivisionForNegatives() {
        assertEquals(0, RegionGrid.regionOf(0));
        assertEquals(0, RegionGrid.regionOf(15));
        assertEquals(1, RegionGrid.regionOf(16));
        assertEquals(-1, RegionGrid.regionOf(-1));
        assertEquals(-1, RegionGrid.regionOf(-16));
        assertEquals(-2, RegionGrid.regionOf(-17));
    }

    @Test
    void firstChunkAndCentreAreConsistent() {
        assertEquals(0, RegionGrid.firstChunkOf(0));
        assertEquals(32, RegionGrid.firstChunkOf(2));
        assertEquals(-32, RegionGrid.firstChunkOf(-2));
        assertEquals(RegionGrid.firstChunkOf(3) * 16 + RegionGrid.REGION_BLOCKS / 2,
                RegionGrid.centreBlockOf(3));
    }

    @Test
    void keyRoundTripsThroughChunkKey() {
        long key = RegionGrid.key(-3, 7);
        assertEquals(-3, RegionGrid.keyX(key));
        assertEquals(7, RegionGrid.keyZ(key));
    }

    @Test
    void regionCountRoundsUp() {
        assertEquals(1, RegionGrid.regionCount(1));
        assertEquals(1, RegionGrid.regionCount(16));
        assertEquals(2, RegionGrid.regionCount(17));
        assertEquals(1, RegionGrid.regionCount(0));
    }
}
