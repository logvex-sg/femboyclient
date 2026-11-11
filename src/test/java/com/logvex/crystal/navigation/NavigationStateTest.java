package com.logvex.crystal.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Route-following cursor: progress, arrival, pause and history. */
class NavigationStateTest {

    private static RoutePlan.Route route(int... xs) {
        List<RoutePlan.Node> nodes = new java.util.ArrayList<>();
        for (int x : xs) {
            nodes.add(new RoutePlan.Node(x, 64, 0));
        }
        return new RoutePlan.Route("r", "", nodes);
    }

    @Test
    void advancingWithinReachMovesTheCursor() {
        NavigationState state = new NavigationState();
        state.start(route(0, 100, 200), 0, 0, 0L);
        assertFalse(state.advance(0, 0, 4.0, 1L));
        assertEquals(1, state.index());
    }

    @Test
    void reachingTheFinalNodeLatchesArrivalOnce() {
        NavigationState state = new NavigationState();
        state.start(route(0, 10), 10, 0, 0L);
        assertTrue(state.advance(10, 0, 4.0, 1L));
        assertTrue(state.arrived());
        // A second advance reports no new arrival.
        assertFalse(state.advance(10, 0, 4.0, 2L));
    }

    @Test
    void pausedStateDoesNotAdvance() {
        NavigationState state = new NavigationState();
        state.start(route(0, 100), 0, 0, 0L);
        state.pause();
        assertFalse(state.advance(0, 0, 4.0, 1L));
        assertEquals(0, state.index());
        state.resume();
        state.advance(0, 0, 4.0, 2L);
        assertEquals(1, state.index());
    }

    @Test
    void progressIsMeasuredFromTheCursor() {
        NavigationState state = new NavigationState();
        state.start(route(0, 100, 200, 300), 0, 0, 0L);
        state.advance(0, 0, 4.0, 1L);
        state.advance(100, 0, 4.0, 2L);
        assertEquals(2.0 / 3.0, state.progressAt(100, 0), 1e-6);
    }

    @Test
    void cancellingRecordsAnIncompleteHistoryEntry() {
        NavigationState state = new NavigationState();
        state.start(route(0, 100, 200), 0, 0, 0L);
        state.advance(0, 0, 4.0, 1L);
        state.cancel(100, 0, 5L);
        assertEquals(1, state.history().size());
        assertFalse(state.history().get(0).completed());
        assertFalse(state.following());
    }

    @Test
    void anArrivalIsRecordedAsCompleted() {
        NavigationState state = new NavigationState();
        state.start(route(0, 10), 10, 0, 0L);
        state.advance(10, 0, 4.0, 1L);
        assertEquals(1, state.history().size());
        assertTrue(state.history().get(0).completed());
    }

    @Test
    void describeReportsNoRouteWhenIdle() {
        assertEquals("no route", new NavigationState().describe(0, 0, 0));
    }
}
