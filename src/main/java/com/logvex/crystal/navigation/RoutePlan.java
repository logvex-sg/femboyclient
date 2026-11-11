package com.logvex.crystal.navigation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.logvex.crystal.config.ConfigIo;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Saved navigation routes: an ordered list of block nodes between waypoints, plus the maths the
 * HUD and the planner need - total distance, the nearest node, the remaining distance, progress,
 * and an obstacle-aware preview over the client's own loaded blocks.
 *
 * <p>A route is pure data. Nothing here moves the player: navigation and movement execution are
 * kept separate, so a route can be previewed and edited with no movement at all. The obstacle
 * check takes a caller-supplied predicate over loaded blocks, so it never loads a chunk itself.
 *
 * <p>Deliberately Minecraft-free so route maths is unit tested without a client.
 */
public final class RoutePlan {

    public static final int SCHEMA_VERSION = 1;

    /** A single block node on a route. */
    public record Node(int x, int y, int z) {
    }

    /** A named route in a dimension. */
    public record Route(String name, String dimension, List<Node> nodes) {
        public Route {
            dimension = dimension == null ? "" : dimension.toLowerCase(Locale.ROOT);
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
        }
    }

    /** The result of a preview pass: how much of a route is clear and how many obstacles sit on it. */
    public record Preview(List<Node> reachable, int obstacles, int totalNodes) {
    }

    private final Path file;
    private final Map<String, Route> routes = new LinkedHashMap<>();

    public RoutePlan(Path file) {
        this.file = file;
    }

    private static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    public void put(Route route) {
        if (route == null || route.name().isBlank()) {
            return;
        }
        routes.put(key(route.name()), route);
    }

    public Route byName(String name) {
        return routes.get(key(name));
    }

    public boolean remove(String name) {
        return routes.remove(key(name)) != null;
    }

    public List<Route> all() {
        return List.copyOf(routes.values());
    }

    public int size() {
        return routes.size();
    }

    // ------------------------------------------------------------------- maths

    /** Total horizontal length of a route, node to node. */
    public static double length(Route route) {
        return length(route.nodes());
    }

    public static double length(List<Node> nodes) {
        double total = 0;
        for (int i = 1; i < nodes.size(); i++) {
            total += horizontalDistance(nodes.get(i - 1), nodes.get(i));
        }
        return total;
    }

    private static double horizontalDistance(Node a, Node b) {
        double dx = b.x() - a.x();
        double dz = b.z() - a.z();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Index of the route node nearest to a point, or -1 for an empty route. */
    public static int nearestIndex(List<Node> nodes, double x, double z) {
        int best = -1;
        double bestSquared = Double.MAX_VALUE;
        for (int i = 0; i < nodes.size(); i++) {
            double dx = nodes.get(i).x() - x;
            double dz = nodes.get(i).z() - z;
            double squared = dx * dx + dz * dz;
            if (squared < bestSquared) {
                bestSquared = squared;
                best = i;
            }
        }
        return best;
    }

    /** Distance still to travel from a point, following the remaining nodes. */
    public static double remaining(List<Node> nodes, double x, double y, double z) {
        int nearest = nearestIndex(nodes, x, z);
        if (nearest < 0) {
            return 0;
        }
        double total = 0;
        double px = x;
        double pz = z;
        for (int i = nearest; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            double dx = node.x() - px;
            double dz = node.z() - pz;
            total += Math.sqrt(dx * dx + dz * dz);
            px = node.x();
            pz = node.z();
        }
        return total;
    }

    /** Progress along a route in {@code [0,1]} from the nearest node's index. */
    public static double progress(List<Node> nodes, double x, double z) {
        if (nodes.size() < 2) {
            return nodes.isEmpty() ? 0 : 1;
        }
        int nearest = nearestIndex(nodes, x, z);
        if (nearest < 0) {
            return 0;
        }
        return (double) nearest / (nodes.size() - 1);
    }

    /**
     * Walks a route and reports which nodes are not blocked, counting the obstacles.
     *
     * <p>{@code blocked} is asked about each node's block column; a node is an obstacle when the
     * predicate says so. This is a preview only: it never edits the route, and it only ever asks
     * about blocks the client already has loaded (the predicate is supplied by the caller).
     */
    public static Preview preview(List<Node> nodes, Predicate<Node> blocked) {
        List<Node> reachable = new ArrayList<>();
        int obstacles = 0;
        for (Node node : nodes) {
            if (blocked.test(node)) {
                obstacles++;
            } else {
                reachable.add(node);
            }
        }
        return new Preview(reachable, obstacles, nodes.size());
    }

    // ------------------------------------------------------------ persistence

    public void save() throws IOException {
        ConfigIo.writeObject(file, toJson());
    }

    public void load() {
        JsonObject root = ConfigIo.readObject(file);
        if (root != null) {
            apply(root);
        }
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        JsonArray array = new JsonArray();
        for (Route route : routes.values()) {
            JsonObject object = new JsonObject();
            object.addProperty("name", route.name());
            object.addProperty("dimension", route.dimension());
            JsonArray nodes = new JsonArray();
            for (Node node : route.nodes()) {
                JsonObject n = new JsonObject();
                n.addProperty("x", node.x());
                n.addProperty("y", node.y());
                n.addProperty("z", node.z());
                nodes.add(n);
            }
            object.add("nodes", nodes);
            array.add(object);
        }
        root.add("routes", array);
        return root;
    }

    /** Applies a parsed document; a newer schema is refused so data is never downgraded. */
    public void apply(JsonObject root) {
        if (root.has("schema") && root.get("schema").isJsonPrimitive()
                && root.get("schema").getAsInt() > SCHEMA_VERSION) {
            return;
        }
        routes.clear();
        if (!root.has("routes") || !root.get("routes").isJsonArray()) {
            return;
        }
        for (JsonElement element : root.getAsJsonArray("routes")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            try {
                String name = object.get("name").getAsString();
                String dimension = object.has("dimension") ? object.get("dimension").getAsString() : "";
                List<Node> nodes = new ArrayList<>();
                if (object.has("nodes") && object.get("nodes").isJsonArray()) {
                    for (JsonElement nodeElement : object.getAsJsonArray("nodes")) {
                        JsonObject n = nodeElement.getAsJsonObject();
                        nodes.add(new Node(n.get("x").getAsInt(), n.get("y").getAsInt(),
                                n.get("z").getAsInt()));
                    }
                }
                routes.put(key(name), new Route(name, dimension, nodes));
            } catch (RuntimeException malformed) {
                // Skip this route, keep the rest.
            }
        }
    }
}
