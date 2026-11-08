package com.logvex.crystal.scan.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests the exploration state machine: safe aborts on invalid conditions, route following,
 * boundary refusal, stall accounting and visited-region dedupe.
 */
class ExplorationAgentTest {

    private static final ScanConfig CONFIG = ScanConfig.defaults();

    private ExplorationAgent agent(ScanBounds bounds, List<RoutePlanner.Waypoint> route, int x, int z) {
        ExplorationAgent agent = new ExplorationAgent(512);
        agent.begin(bounds, route, "minecraft:overworld", x, z, 0);
        return agent;
    }

    @Test
    void abortsWhenWorldIsNotLoaded() {
        ExplorationAgent agent = agent(new ScanBounds(0, 0, 1, 1), List.of(new RoutePlanner.Waypoint(8, 8)), 8, 8);
        ExplorationAgent.Decision decision = agent.tick("minecraft:overworld", CONFIG, false, true);
        assertTrue(decision.abort());
        assertEquals("world unloaded", decision.message());
    }

    @Test
    void abortsWhenPlayerIsNotAlive() {
        ExplorationAgent agent = agent(new ScanBounds(0, 0, 1, 1), List.of(new RoutePlanner.Waypoint(8, 8)), 8, 8);
        assertTrue(agent.tick("minecraft:overworld", CONFIG, true, false).abort());
    }

    @Test
    void abortsOnDimensionChange() {
        ExplorationAgent agent = agent(new ScanBounds(0, 0, 1, 1), List.of(new RoutePlanner.Waypoint(8, 8)), 8, 8);
        ExplorationAgent.Decision decision = agent.tick("minecraft:the_nether", CONFIG, true, true);
        assertTrue(decision.abort());
        assertTrue(decision.message().contains("dimension"));
    }

    @Test
    void emptyRouteCompletesImmediately() {
        ExplorationAgent agent = agent(new ScanBounds(0, 0, 1, 1), List.of(), 8, 8);
        assertTrue(agent.tick("minecraft:overworld", CONFIG, true, true).finished());
    }

    @Test
    void movesTowardAFarWaypoint() {
        ExplorationAgent agent = agent(new ScanBounds(0, 0, 64, 64), List.of(new RoutePlanner.Waypoint(8, 8)),
                1000, 1000);
        ExplorationAgent.Decision decision = agent.tick("minecraft:overworld", CONFIG, true, true);
        assertTrue(decision.moveToWaypoint());
        assertEquals(8, decision.waypointX());
        assertEquals(1, agent.waypointStallTicks());
    }

    @Test
    void completesWhenAllWaypointsAreReached() {
        ScanBounds bounds = new ScanBounds(0, 0, 0, 0);
        List<RoutePlanner.Waypoint> route = List.of(new RoutePlanner.Waypoint(8, 8));
        ExplorationAgent agent = agent(bounds, route, 8, 8);
        assertTrue(agent.tick("minecraft:overworld", CONFIG, true, true).finished());
    }

    @Test
    void refusesAWaypointOutsideBounds() {
        // Bounds cover chunk 0 only; the waypoint sits in chunk 5.
        ScanBounds bounds = new ScanBounds(0, 0, 0, 0);
        List<RoutePlanner.Waypoint> route = List.of(new RoutePlanner.Waypoint(88, 8));
        ExplorationAgent agent = agent(bounds, route, 8, 8);
        ExplorationAgent.Decision decision = agent.tick("minecraft:overworld", CONFIG, true, true);
        assertTrue(decision.finished(), "an out-of-bounds waypoint must be refused, finishing the route");
    }

    @Test
    void stallCounterRisesAndClears() {
        ExplorationAgent agent = agent(new ScanBounds(0, 0, 64, 64), List.of(new RoutePlanner.Waypoint(8, 8)),
                1000, 1000);
        for (int i = 0; i < 5; i++) {
            agent.tick("minecraft:overworld", CONFIG, true, true);
        }
        assertEquals(5, agent.waypointStallTicks());
        agent.clearStall();
        assertEquals(0, agent.waypointStallTicks());
    }

    @Test
    void skipWaypointAdvancesTheIndex() {
        List<RoutePlanner.Waypoint> route = List.of(
                new RoutePlanner.Waypoint(8, 8), new RoutePlanner.Waypoint(108, 8));
        ExplorationAgent agent = agent(new ScanBounds(0, 0, 64, 64), route, 1000, 1000);
        agent.skipWaypoint();
        assertEquals(1, agent.routeIndex());
    }

    @Test
    void visitedRegionsAreDedupedAndBounded() {
        ExplorationAgent agent = new ExplorationAgent(256);
        agent.begin(new ScanBounds(0, 0, 1, 1), List.of(), "d", 0, 0, 0);
        agent.markVisited(1, 1);
        assertTrue(agent.alreadyVisited(1, 1));
        agent.markVisited(1, 1);
        assertEquals(1, agent.visitedCount());
        for (int i = 0; i < 400; i++) {
            agent.markVisited(i, 2);
        }
        assertTrue(agent.visitedCount() <= 256);
        assertFalse(agent.alreadyVisited(1, 1), "oldest visited entries are evicted first");
    }
}
