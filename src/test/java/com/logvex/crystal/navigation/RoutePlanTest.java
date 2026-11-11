package com.logvex.crystal.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Route maths and route persistence. */
class RoutePlanTest {

    private static RoutePlan.Node n(int x, int y, int z) {
        return new RoutePlan.Node(x, y, z);
    }

    @Test
    void lengthSumsTheHorizontalLegs() {
        RoutePlan.Route route = new RoutePlan.Route("line", "overworld",
                List.of(n(0, 64, 0), n(30, 64, 40), n(30, 64, 40)));
        // 30-40-50 triangle: legs of 50 then 0.
        assertEquals(50.0, RoutePlan.length(route), 1e-6);
    }

    @Test
    void nearestIndexPicksTheClosestNode() {
        List<RoutePlan.Node> nodes = List.of(n(0, 64, 0), n(100, 64, 0), n(200, 64, 0));
        assertEquals(1, RoutePlan.nearestIndex(nodes, 110, 5));
    }

    @Test
    void remainingCountsFromTheNearestNodeForward() {
        List<RoutePlan.Node> nodes = List.of(n(0, 64, 0), n(100, 64, 0), n(200, 64, 0));
        assertEquals(200.0, RoutePlan.remaining(nodes, 0, 64, 0), 1e-6);
    }

    @Test
    void progressIsTheFractionOfNodesPassed() {
        List<RoutePlan.Node> nodes = List.of(n(0, 64, 0), n(100, 64, 0), n(200, 64, 0));
        assertEquals(0.5, RoutePlan.progress(nodes, 100, 0), 1e-6);
    }

    @Test
    void previewSeparatesClearNodesFromObstacles() {
        List<RoutePlan.Node> nodes = List.of(n(0, 64, 0), n(16, 64, 0), n(32, 64, 0));
        RoutePlan.Preview preview = RoutePlan.preview(nodes, node -> node.x() == 16);
        assertEquals(2, preview.reachable().size());
        assertEquals(1, preview.obstacles());
        assertEquals(3, preview.totalNodes());
    }

    @Test
    void routesRoundTripThroughJson(@TempDir Path dir) {
        RoutePlan plan = new RoutePlan(dir.resolve("nav.json"));
        plan.put(new RoutePlan.Route("home", "minecraft:overworld",
                List.of(n(0, 70, 0), n(50, 70, 50))));
        plan.put(new RoutePlan.Route("portal", "minecraft:the_nether",
                List.of(n(-10, 40, -10), n(10, 40, 10))));

        RoutePlan reloaded = new RoutePlan(dir.resolve("nav.json"));
        reloaded.apply(plan.toJson());
        assertEquals(2, reloaded.size());
        assertEquals(2, reloaded.byName("HOME").nodes().size());
        assertEquals("minecraft:the_nether", reloaded.byName("portal").dimension());
    }

    @Test
    void aNewerSchemaIsRefused(@TempDir Path dir) {
        RoutePlan plan = new RoutePlan(dir.resolve("nav.json"));
        plan.put(new RoutePlan.Route("home", "", List.of(n(0, 64, 0), n(1, 64, 1))));
        JsonObject future = plan.toJson();
        future.addProperty("schema", RoutePlan.SCHEMA_VERSION + 1);

        RoutePlan target = new RoutePlan(dir.resolve("nav.json"));
        target.apply(future);
        assertEquals(0, target.size());
    }

    @Test
    void removingARouteByNameIsCaseInsensitive(@TempDir Path dir) {
        RoutePlan plan = new RoutePlan(dir.resolve("nav.json"));
        plan.put(new RoutePlan.Route("Home", "", List.of(n(0, 64, 0), n(1, 64, 1))));
        assertTrue(plan.remove("home"));
        assertEquals(0, plan.size());
    }
}
