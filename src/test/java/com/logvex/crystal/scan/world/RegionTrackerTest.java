package com.logvex.crystal.scan.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests region progress tracking: visited chunks, complete regions and unexplored listing. */
class RegionTrackerTest {

    @Test
    void observingChunksCountsDistinctly() {
        RegionTracker tracker = new RegionTracker();
        tracker.observe("d", 0, 0);
        tracker.observe("d", 1, 0);
        tracker.observe("d", 0, 0); // duplicate
        assertEquals(2, tracker.visitedChunks("d"));
        assertEquals(1, tracker.touchedRegions("d"));
    }

    @Test
    void aRegionIsCompleteOnlyWhenEveryChunkIsSeen() {
        RegionTracker tracker = new RegionTracker();
        for (int x = 0; x < RegionGrid.REGION_CHUNKS; x++) {
            for (int z = 0; z < RegionGrid.REGION_CHUNKS; z++) {
                tracker.observe("d", x, z);
            }
        }
        assertEquals(1, tracker.completeRegions("d"));
        assertTrue(tracker.regionComplete("d", 0, 0));
        assertFalse(tracker.regionComplete("d", 100, 100));
    }

    @Test
    void partialRegionIsNotComplete() {
        RegionTracker tracker = new RegionTracker();
        tracker.observe("d", 0, 0);
        assertFalse(tracker.regionComplete("d", 0, 0));
        assertEquals(0, tracker.completeRegions("d"));
    }

    @Test
    void unexploredListingCoversTheArea() {
        RegionTracker tracker = new RegionTracker();
        // Observe one whole region at (0,0).
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                tracker.observe("d", x, z);
            }
        }
        // A 32x32-chunk area is 2x2 regions; only (0,0) is touched.
        List<Long> unexplored = tracker.unexploredRegions("d", 0, 0, 31, 31);
        assertEquals(3, unexplored.size());
        assertFalse(unexplored.contains(RegionGrid.key(0, 0)));
    }

    @Test
    void dimensionsAreIndependent() {
        RegionTracker tracker = new RegionTracker();
        tracker.observe("overworld", 0, 0);
        tracker.observe("nether", 5, 5);
        assertEquals(1, tracker.visitedChunks("overworld"));
        assertEquals(1, tracker.visitedChunks("nether"));
        assertEquals(2, tracker.dimensions().size());
        tracker.clearDimension("nether");
        assertEquals(0, tracker.visitedChunks("nether"));
        assertEquals(1, tracker.visitedChunks("overworld"));
    }

    @Test
    void bitmapExposesVisitedSlots() {
        RegionTracker tracker = new RegionTracker();
        tracker.observe("d", 3, 2);
        int[] bitmap = tracker.regionBitmap("d", 0, 0);
        assertNotNull(bitmap);
        assertEquals(1, bitmap[2 * 16 + 3]);
        assertEquals(0, bitmap[0]);
    }
}
