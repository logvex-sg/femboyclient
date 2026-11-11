package com.logvex.crystal.navigation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Live route-following state: which route is active, how far along it the player is, and the
 * arrival log. It is the only part of navigation that holds a cursor, and it is pure - it reads a
 * position and reports progress, it never moves the player. Movement execution stays separate, so
 * a route can be followed by the player, by Baritone, or not at all.
 *
 * <p>Reaching a node is a radius test rather than an exact-match, so a fast traversal that clips a
 * node still advances instead of stalling. Arriving at the final node latches {@link #arrived()}
 * once and is recorded in the history; the caller decides what to do with it.
 */
public final class NavigationState {

    /** A finished or cancelled route, kept for the route history. */
    public record Arrival(String routeName, boolean completed, double travelledBlocks, long atMillis) {
    }

    private static final int HISTORY_LIMIT = 50;

    private RoutePlan.Route active;
    private int index;
    private boolean paused;
    private boolean arrived;
    private double startDistance;
    private final Deque<Arrival> history = new ArrayDeque<>();

    /** Begin following a route from its nearest node. */
    public void start(RoutePlan.Route route, double x, double z, long nowMillis) {
        this.active = route;
        this.paused = false;
        this.arrived = false;
        List<RoutePlan.Node> nodes = route == null ? List.of() : route.nodes();
        this.index = Math.max(0, RoutePlan.nearestIndex(nodes, x, z));
        this.startDistance = RoutePlan.remaining(nodes, x, 0, z);
    }

    public boolean following() {
        return active != null && !arrived;
    }

    public boolean paused() {
        return paused;
    }

    public boolean arrived() {
        return arrived;
    }

    public void pause() {
        if (active != null && !arrived) {
            paused = true;
        }
    }

    public void resume() {
        paused = false;
    }

    /** Cancel the active route and record it as incomplete. */
    public void cancel(double x, double z, long nowMillis) {
        if (active == null) {
            return;
        }
        double travelled = Math.max(0, startDistance - RoutePlan.remaining(active.nodes(), x, 0, z));
        record(new Arrival(active.name(), false, travelled, nowMillis));
        active = null;
        index = 0;
        paused = false;
        arrived = false;
    }

    public RoutePlan.Route active() {
        return active;
    }

    public int index() {
        return index;
    }

    /** Progress from the tracked cursor rather than the nearest node, for a stable bar. */
    public double progressAt(double x, double z) {
        if (active == null || active.nodes().isEmpty()) {
            return 0;
        }
        return (double) Math.min(index, active.nodes().size() - 1)
                / Math.max(1, active.nodes().size() - 1);
    }

    public double remaining(double x, double y, double z) {
        if (active == null) {
            return 0;
        }
        return RoutePlan.remaining(active.nodes(), x, y, z);
    }

    /**
     * Advance the cursor past every node within {@code reach} of the player.
     *
     * @return true the first time the final node is reached (an arrival), false otherwise
     */
    public boolean advance(double x, double z, double reach, long nowMillis) {
        if (active == null || paused || arrived) {
            return false;
        }
        List<RoutePlan.Node> nodes = active.nodes();
        double reachSquared = reach * reach;
        while (index < nodes.size()) {
            RoutePlan.Node node = nodes.get(index);
            double dx = node.x() - x;
            double dz = node.z() - z;
            if (dx * dx + dz * dz <= reachSquared) {
                index++;
            } else {
                break;
            }
        }
        if (index >= nodes.size() && !nodes.isEmpty()) {
            arrived = true;
            double travelled = Math.max(0, startDistance - RoutePlan.remaining(nodes, x, 0, z));
            record(new Arrival(active.name(), true, travelled, nowMillis));
            return true;
        }
        return false;
    }

    /** Clear the arrival latch so the same route can be followed again. */
    public void clearArrival() {
        arrived = false;
    }

    private void record(Arrival arrival) {
        history.addFirst(arrival);
        while (history.size() > HISTORY_LIMIT) {
            history.removeLast();
        }
    }

    /** Route history, newest first. */
    public List<Arrival> history() {
        return new ArrayList<>(history);
    }

    public void clearHistory() {
        history.clear();
    }

    /** A short line for the HUD: the route name and progress, or a status. */
    public String describe(double x, double y, double z) {
        if (active == null) {
            return "no route";
        }
        if (arrived) {
            return active.name() + " arrived";
        }
        int percent = (int) Math.round(progressAt(x, z) * 100);
        return active.name() + " " + percent + "% \u00b7 "
                + Math.round(remaining(x, y, z)) + "m" + (paused ? " (paused)" : "");
    }
}
