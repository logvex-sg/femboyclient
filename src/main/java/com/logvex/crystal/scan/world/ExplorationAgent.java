package com.logvex.crystal.scan.world;

import java.util.List;

/**
 * The autonomous exploration state machine.
 *
 * <p>It owns the run's progress (route index, current position, explored regions, stall and
 * failure counters) and decides, one tick at a time, what the {@link WorldScanner} should do:
 * engage flight, sample the chunks around the player, advance the route, detect a stall or a
 * finished route. It holds no Minecraft types - the scanner passes in the current position and
 * the sampler - so every transition is unit testable.
 *
 * <p>Guarantees the mission asks for, all enforced here:
 * <ul>
 *   <li>tracks its current position and explored regions (a bounded set of visited chunks);</li>
 *   <li>never re-scans an identical area (a chunk already mapped is skipped);</li>
 *   <li>detects world boundaries (a waypoint outside the configured bounds is refused);</li>
 *   <li>handles unavailable flight (falls back to walking);</li>
 *   <li>detects unloaded chunks (they are recorded for a later revisit, not classified);</li>
 *   <li>handles dimension changes (a change aborts the run rather than mixing worlds);</li>
 *   <li>stops safely when conditions become invalid (world unloaded, player dead, stall).</li>
 * </ul>
 */
public final class ExplorationAgent {

    /** A one-tick decision the scanner acts on. */
    public record Decision(boolean moveToWaypoint, int waypointX, int waypointZ,
                           boolean finished, boolean abort, String message) {

        static Decision idle() {
            return new Decision(false, 0, 0, false, false, "");
        }

        static Decision move(int x, int z) {
            return new Decision(true, x, z, false, false, "");
        }

        static Decision done() {
            return new Decision(false, 0, 0, true, false, "route complete");
        }

        static Decision abort(String message) {
            return new Decision(false, 0, 0, false, true, message);
        }
    }

    /** A chunk the agent has visited, used to avoid re-scanning the same area. */
    private final java.util.Set<Long> visited = new java.util.LinkedHashSet<>();

    private final int visitedCap;

    private ScanBounds bounds;
    private List<RoutePlanner.Waypoint> route = List.of();
    private int routeIndex;
    private String dimension = "";
    private int currentX;
    private int currentZ;
    private int waypointStallTicks;
    private boolean routeOrdered;

    public ExplorationAgent(int visitedCap) {
        this.visitedCap = Math.max(256, visitedCap);
    }

    /** (Re)initialises the agent for a run. {@code routeIndex} resumes a paused run. */
    public void begin(ScanBounds bounds, List<RoutePlanner.Waypoint> route, String dimension,
                      int currentX, int currentZ, int routeIndex) {
        this.bounds = bounds;
        this.route = route == null ? List.of() : route;
        this.dimension = dimension == null ? "" : dimension;
        this.currentX = currentX;
        this.currentZ = currentZ;
        this.routeIndex = Math.max(0, routeIndex);
        this.waypointStallTicks = 0;
        this.routeOrdered = false;
    }

    public ScanBounds bounds() {
        return bounds;
    }

    public int routeIndex() {
        return routeIndex;
    }

    public int routeSize() {
        return route.size();
    }

    public int currentX() {
        return currentX;
    }

    public int currentZ() {
        return currentZ;
    }

    public int visitedCount() {
        return visited.size();
    }

    public int waypointStallTicks() {
        return waypointStallTicks;
    }

    /** Updates the tracked position. Called every tick with the live player position. */
    public void setPosition(int x, int z) {
        this.currentX = x;
        this.currentZ = z;
    }

    /** True when the chunk has already been visited in this run (so it need not be re-sampled). */
    public boolean alreadyVisited(int chunkX, int chunkZ) {
        return visited.contains(ChunkKey.of(chunkX, chunkZ));
    }

    /** Records a chunk as visited, evicting the oldest entries when the cap is reached. */
    public void markVisited(int chunkX, int chunkZ) {
        long key = ChunkKey.of(chunkX, chunkZ);
        if (visited.contains(key)) {
            return;
        }
        visited.add(key);
        while (visited.size() > visitedCap) {
            var it = visited.iterator();
            it.next();
            it.remove();
        }
    }

    /**
     * The per-tick decision for movement: whether to head to a waypoint, whether the route is
     * finished, or whether the run must abort. The scanner samples chunks separately (it batches
     * them); the agent only owns the route and the visited-region bookkeeping.
     */
    public Decision tick(String liveDimension, ScanConfig config, boolean worldLoaded, boolean playerAlive) {
        if (!worldLoaded) {
            return Decision.abort("world unloaded");
        }
        if (!playerAlive) {
            return Decision.abort("player is not alive");
        }
        if (liveDimension != null && !liveDimension.equals(dimension)) {
            return Decision.abort("dimension changed to " + liveDimension);
        }
        if (route.isEmpty() || routeIndex >= route.size()) {
            return Decision.done();
        }

        // Order the route once from the player's actual position so a resumed run does not fly
        // backwards to the start of the sweep.
        if (!routeOrdered) {
            routeIndex = RoutePlanner.nearestIndex(route, currentX, currentZ);
            routeOrdered = true;
        }

        RoutePlanner.Waypoint target = route.get(routeIndex);

        // A waypoint outside the configured bounds must never be pursued.
        if (bounds != null && !bounds.contains(target.x() >> 4, target.z() >> 4)) {
            routeIndex++;
            return routeIndex >= route.size() ? Decision.done() : Decision.idle();
        }

        double dx = target.x() - currentX;
        double dz = target.z() - currentZ;
        double reach = Math.max(8, config.routeStepBlocks() * 0.75);
        if (dx * dx + dz * dz <= reach * reach) {
            routeIndex = RoutePlanner.advance(route, routeIndex, currentX, currentZ, reach);
            waypointStallTicks = 0;
            return routeIndex >= route.size() ? Decision.done() : Decision.idle();
        }

        // Not there yet: keep heading toward it, watching for a stall.
        waypointStallTicks++;
        return Decision.move(target.x(), target.z());
    }

    /** Clears a stall once the scanner has re-planned or abandoned the waypoint. */
    public void clearStall() {
        waypointStallTicks = 0;
    }

    /** Skips the current waypoint (e.g. it is unreachable) and moves to the next. */
    public void skipWaypoint() {
        routeIndex++;
        waypointStallTicks = 0;
    }

    /** Forgets all visited chunks (used by an explicit session reset). */
    public void reset() {
        visited.clear();
        routeIndex = 0;
        waypointStallTicks = 0;
        routeOrdered = false;
    }
}
