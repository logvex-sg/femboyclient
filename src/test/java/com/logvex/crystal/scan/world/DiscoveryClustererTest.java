package com.logvex.crystal.scan.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests the discovery clusterer: flood-fill grouping, scoring/ranking, nearest-neighbour touring,
 * the frontier push and visited-target suppression.
 */
class DiscoveryClustererTest {

    private static long chunk(int x, int z) {
        return ChunkKey.of(x, z);
    }

    @Test
    void adjacentChunksCollapseIntoOneCluster() {
        Set<Long> positions = Set.of(chunk(0, 0), chunk(1, 0), chunk(0, 1), chunk(1, 1));
        List<List<Long>> clusters = DiscoveryClusterer.cluster(positions, 1);
        assertEquals(1, clusters.size());
        assertEquals(4, clusters.get(0).size());
    }

    @Test
    void distantChunksStaySeparate() {
        Set<Long> positions = Set.of(chunk(0, 0), chunk(20, 20));
        List<List<Long>> clusters = DiscoveryClusterer.cluster(positions, 1);
        assertEquals(2, clusters.size());
    }

    @Test
    void targetsAreRankedBySizeThenDistance() {
        DiscoveryClusterer clusterer = new DiscoveryClusterer();
        Set<Long> positions = Set.of(chunk(0, 0), chunk(1, 0), chunk(1, 1), chunk(50, 50));
        List<DiscoveryClusterer.Cluster> targets =
                clusterer.buildTargets(positions, Set.of(), 1, 0, 0, 10);
        assertEquals(2, targets.size());
        // The 3-chunk cluster scores higher than the single distant one.
        assertEquals(3, targets.get(0).count());
        assertTrue(targets.get(0).score() > targets.get(1).score());
    }

    @Test
    void notableClusterGetsABonus() {
        DiscoveryClusterer clusterer = new DiscoveryClusterer();
        Set<Long> positions = Set.of(chunk(0, 0), chunk(40, 0));
        List<DiscoveryClusterer.Cluster> targets =
                clusterer.buildTargets(positions, Set.of(chunk(40, 0)), 1, 0, 0, 10);
        assertEquals(2, targets.size());
        assertEquals(40, targets.get(0).centreBlockX() >> 4);
        assertTrue(targets.get(0).reason().contains("notable"));
    }

    @Test
    void visitedTargetsAreExcluded() {
        DiscoveryClusterer clusterer = new DiscoveryClusterer();
        Set<Long> positions = Set.of(chunk(0, 0));
        clusterer.markVisited(8, 8);
        assertTrue(clusterer.buildTargets(positions, Set.of(), 1, 0, 0, 10).isEmpty());
        assertTrue(clusterer.wasVisited(8, 8));
        assertEquals(1, clusterer.visitedCount());
    }

    @Test
    void tourOrdersNearestFirst() {
        List<DiscoveryClusterer.Cluster> targets = List.of(
                new DiscoveryClusterer.Cluster(1000, 0, 1, 1, "far"),
                new DiscoveryClusterer.Cluster(10, 0, 1, 1, "near"),
                new DiscoveryClusterer.Cluster(500, 0, 1, 1, "mid"));
        List<DiscoveryClusterer.Cluster> ordered = DiscoveryClusterer.tour(targets, 0, 0);
        assertEquals("near", ordered.get(0).reason());
        assertEquals("mid", ordered.get(1).reason());
        assertEquals("far", ordered.get(2).reason());
    }

    @Test
    void frontierPushesOutwardAndStaysInBounds() {
        DiscoveryClusterer clusterer = new DiscoveryClusterer();
        ScanBounds bounds = ScanBounds.radius(0, 0, 64);
        // A discovery at the player's own position gives no direction: no frontier.
        assertNull(clusterer.frontier(Set.of(chunk(0, 0)), bounds, 8, 8));
        // An off-origin discovery pushes the frontier further out along that direction.
        DiscoveryClusterer.Cluster pushed = clusterer.frontier(Set.of(chunk(20, 0)), bounds, 0, 0);
        assertNotNull(pushed);
        assertTrue(pushed.centreBlockX() > 20 * 16);
        assertTrue(pushed.centreBlockX() <= (bounds.maxChunkX() << 4) + 15);
    }

    @Test
    void frontierClampsToBounds() {
        DiscoveryClusterer clusterer = new DiscoveryClusterer();
        ScanBounds small = new ScanBounds(0, 0, 4, 4);
        DiscoveryClusterer.Cluster frontier = clusterer.frontier(Set.of(chunk(3, 3)), small, 0, 0);
        assertNotNull(frontier);
        assertTrue(frontier.centreBlockX() <= (small.maxChunkX() << 4) + 15);
        assertTrue(frontier.centreBlockZ() <= (small.maxChunkZ() << 4) + 15);
    }

    @Test
    void progressClockAdvances() {
        DiscoveryClusterer clusterer = new DiscoveryClusterer();
        assertTrue(clusterer.millisSinceProgress() < 1000);
        clusterer.noteProgress();
        assertTrue(clusterer.millisSinceProgress() < 1000);
        clusterer.reset();
        assertEquals(0, clusterer.visitedCount());
    }
}
