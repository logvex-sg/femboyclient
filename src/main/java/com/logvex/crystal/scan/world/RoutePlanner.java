package com.logvex.crystal.scan.world;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates an exploration route over a {@link ScanBounds} region.
 *
 * <p>The route is a boustrophedon ("snake") sweep of chunk centres: it walks one row west to
 * east, steps one row south, walks east to west, and so on. This covers the region with no gaps
 * and minimal backtracking, which keeps both the distance flown and the number of direction
 * changes low. Waypoints are chunk centres subsampled by {@code stepBlocks}, so a coarse step
 * produces a fast fly-over and a fine step a thorough sweep.
 *
 * <p>Pure and Minecraft-free: the agent tracks the current index; this class only produces the
 * route and answers "which waypoint is next / nearest".
 */
public final class RoutePlanner {

    /** A route waypoint, in block coordinates. */
    public record Waypoint(int x, int z) {
    }

    private RoutePlanner() {
    }

    /**
     * Plans a snake route over {@code bounds}. {@code stepBlocks} is rounded to a whole number of
     * chunks (at least one), so waypoints always land on chunk centres.
     */
    public static List<Waypoint> plan(ScanBounds bounds, int stepBlocks) {
        List<Waypoint> route = new ArrayList<>();
        int stepChunks = Math.max(1, stepBlocks / 16);
        int row = 0;
        for (int z = bounds.minChunkZ(); z <= bounds.maxChunkZ(); z += stepChunks) {
            boolean leftToRight = (row % 2) == 0;
            if (leftToRight) {
                for (int x = bounds.minChunkX(); x <= bounds.maxChunkX(); x += stepChunks) {
                    route.add(waypoint(x, z));
                }
            } else {
                for (int x = bounds.maxChunkX() - ((bounds.maxChunkX() - bounds.minChunkX()) % stepChunks);
                     x >= bounds.minChunkX(); x -= stepChunks) {
                    route.add(waypoint(x, z));
                }
            }
            row++;
        }
        return route;
    }

    private static Waypoint waypoint(int chunkX, int chunkZ) {
        return new Waypoint((chunkX << 4) + 8, (chunkZ << 4) + 8);
    }

    /** The index of the route waypoint nearest to {@code (x,z)}, or 0 for an empty route. */
    public static int nearestIndex(List<Waypoint> route, int x, int z) {
        int best = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < route.size(); i++) {
            Waypoint w = route.get(i);
            long dx = w.x() - x;
            long dz = w.z() - z;
            long distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    /**
     * Advances the index past every waypoint within {@code reachDistance} of {@code (x,z)}.
     * Returns the new index, which is {@code route.size()} when the route is finished. Using a
     * reach radius rather than exact arrival lets a fast fly-over skip a waypoint it clipped
     * without stalling the run.
     */
    public static int advance(List<Waypoint> route, int index, int x, int z, double reachDistance) {
        double reachSquared = reachDistance * reachDistance;
        int i = Math.max(0, index);
        while (i < route.size()) {
            Waypoint w = route.get(i);
            double dx = w.x() - x;
            double dz = w.z() - z;
            if (dx * dx + dz * dz <= reachSquared) {
                i++;
            } else {
                break;
            }
        }
        return i;
    }

    /** True when the index is at or past the end of the route. */
    public static boolean finished(List<Waypoint> route, int index) {
        return index >= route.size();
    }
}
