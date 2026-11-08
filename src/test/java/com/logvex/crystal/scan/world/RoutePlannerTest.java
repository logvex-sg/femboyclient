package com.logvex.crystal.scan.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests route generation: a gap-free snake sweep, nearest-waypoint lookup and reach-based advance. */
class RoutePlannerTest {

    @Test
    void snakeRouteCoversEveryRowWithoutGaps() {
        ScanBounds bounds = new ScanBounds(0, 0, 3, 1);
        List<RoutePlanner.Waypoint> route = RoutePlanner.plan(bounds, 16);
        // Two rows (z=0,1) of four waypoints each.
        assertEquals(8, route.size());
        // First row runs left to right.
        assertEquals(8, route.get(0).x());
        assertEquals(24, route.get(1).x());
        assertEquals(40, route.get(2).x());
        assertEquals(56, route.get(3).x());
        // Second row runs right to left (a boustrophedon), starting at the east edge.
        assertEquals(56, route.get(4).x());
        assertEquals(8, route.get(7).x());
        // Every waypoint is a chunk centre.
        for (RoutePlanner.Waypoint waypoint : route) {
            assertEquals(8, Math.floorMod(waypoint.x(), 16));
            assertEquals(8, Math.floorMod(waypoint.z(), 16));
        }
    }

    @Test
    void coarseStepProducesFewerWaypoints() {
        ScanBounds bounds = new ScanBounds(0, 0, 15, 15);
        assertTrue(RoutePlanner.plan(bounds, 16).size() > RoutePlanner.plan(bounds, 64).size());
    }

    @Test
    void nearestIndexFindsTheClosestWaypoint() {
        List<RoutePlanner.Waypoint> route = List.of(
                new RoutePlanner.Waypoint(0, 0),
                new RoutePlanner.Waypoint(100, 0),
                new RoutePlanner.Waypoint(200, 0));
        assertEquals(1, RoutePlanner.nearestIndex(route, 95, 3));
        assertEquals(2, RoutePlanner.nearestIndex(route, 1000, 0));
        assertEquals(0, RoutePlanner.nearestIndex(List.of(), 5, 5));
    }

    @Test
    void advanceSkipsWaypointsWithinReach() {
        List<RoutePlanner.Waypoint> route = List.of(
                new RoutePlanner.Waypoint(0, 0),
                new RoutePlanner.Waypoint(10, 0),
                new RoutePlanner.Waypoint(50, 0));
        // From (2,0) both the first and second waypoints are within an 8-block reach.
        int index = RoutePlanner.advance(route, 0, 2, 0, 8.0);
        assertEquals(2, index);
        assertFalse(RoutePlanner.finished(route, index));
        assertEquals(3, RoutePlanner.advance(route, index, 50, 0, 8.0));
        assertTrue(RoutePlanner.finished(route, 3));
    }

    @Test
    void advanceDoesNotSkipAReachablePrefixOutOfOrder() {
        // A waypoint that is still out of reach must not be skipped just because a later one is
        // closer: the route is followed in order.
        List<RoutePlanner.Waypoint> route = List.of(
                new RoutePlanner.Waypoint(0, 0),
                new RoutePlanner.Waypoint(10, 0));
        assertEquals(0, RoutePlanner.advance(route, 0, 12, 0, 8.0));
    }
}
