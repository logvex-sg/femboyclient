package com.logvex.crystal.navigation;

import com.logvex.crystal.notification.NotificationLevel;
import com.logvex.crystal.notification.NotificationManager;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;

/**
 * The client-facing navigation engine: it owns the {@link RoutePlan} store, the live
 * {@link NavigationState} cursor and the recent-position / death bookkeeping, and ticks the
 * route follower once per client tick.
 *
 * <p>It is careful about what it does on the tick: it reads the player position, advances the
 * cursor and - only when a route arrives - raises a notification. It never moves the player and
 * never scans the world. Movement execution is deliberately left to the player or to Baritone, so
 * the navigation layer stays a pure planner plus a position cursor.
 */
public final class NavigationService {

    /** How many recent positions to keep for the position-history HUD. */
    private static final int HISTORY_LIMIT = 240;
    /** A new history sample is taken at most this often. */
    private static final long SAMPLE_INTERVAL_MILLIS = 250L;

    private final RoutePlan routes;
    private final WaypointRegistry registry;
    private final NavigationState state = new NavigationState();

    private final java.util.ArrayDeque<Position> positions = new java.util.ArrayDeque<>();
    private Position lastPosition;
    private Position deathPosition;
    private long deathAtMillis;
    private long lastSampleAt;
    private boolean wasDead;

    /** A timestamped block position. */
    public record Position(int x, int y, int z, String dimension, long atMillis) {
    }

    public NavigationService(RoutePlan routes, WaypointRegistry registry) {
        this.routes = routes;
        this.registry = registry;
    }

    public RoutePlan routes() {
        return routes;
    }

    public WaypointRegistry registry() {
        return registry;
    }

    public NavigationState state() {
        return state;
    }

    /** Start following a saved route by name. Returns false when it does not exist. */
    public boolean startRoute(String name, MinecraftClient client) {
        RoutePlan.Route route = routes.byName(name);
        if (route == null || client.player == null) {
            return false;
        }
        var pos = client.player.getEntityPos();
        state.start(route, pos.x, pos.z, System.currentTimeMillis());
        return true;
    }

    /** Cancel the active route. Returns false when nothing was being followed. */
    public boolean cancelRoute(MinecraftClient client) {
        if (state.active() == null) {
            return false;
        }
        var pos = client.player == null ? null : client.player.getEntityPos();
        state.cancel(pos == null ? 0 : pos.x, pos == null ? 0 : pos.z, System.currentTimeMillis());
        return true;
    }

    /** The most recent sampled positions, newest first. */
    public java.util.List<Position> recentPositions() {
        return new java.util.ArrayList<>(positions);
    }

    public Position lastPosition() {
        return lastPosition;
    }

    public Position deathPosition() {
        return deathPosition;
    }

    /** Records a death location (called by AutoRespawn and the death observer). */
    public void recordDeath(int x, int y, int z, String dimension) {
        deathPosition = new Position(x, y, z, dimension == null ? "" : dimension, System.currentTimeMillis());
        deathAtMillis = System.currentTimeMillis();
    }

    public long deathAtMillis() {
        return deathAtMillis;
    }

    /** Ticks the route follower and samples the position history. Never moves the player. */
    public void tick(MinecraftClient client, NotificationManager notifications) {
        if (client == null || client.player == null || client.world == null) {
            return;
        }
        var pos = client.player.getEntityPos();
        String dimension = client.world.getRegistryKey().getValue().toString();
        long now = System.currentTimeMillis();
        lastPosition = new Position((int) Math.floor(pos.x), (int) Math.floor(pos.y),
                (int) Math.floor(pos.z), dimension, now);
        // Detect the moment of death once, from the previous position, so the death marker is
        // the spot the player fell rather than the respawn point.
        boolean dead = client.player.isDead();
        if (dead && !wasDead) {
            recordDeath(lastPosition.x(), lastPosition.y(), lastPosition.z(), dimension);
        }
        wasDead = dead;
        if (now - lastSampleAt >= SAMPLE_INTERVAL_MILLIS) {
            lastSampleAt = now;
            positions.addFirst(lastPosition);
            while (positions.size() > HISTORY_LIMIT) {
                positions.removeLast();
            }
        }
        if (state.following()) {
            boolean arrived = state.advance(pos.x, pos.z, 4.0, now);
            if (arrived && notifications != null) {
                notifications.notify("Route '" + state.active().name() + "' reached \u2661",
                        NotificationLevel.SUCCESS);
            }
        }
    }

    /** A one-line navigation summary for the compass HUD. */
    public String describe(MinecraftClient client) {
        if (client == null || client.player == null) {
            return "no route";
        }
        var pos = client.player.getEntityPos();
        return state.describe(pos.x, pos.y, pos.z);
    }

    /** The active route name, or empty. */
    public String activeRouteName() {
        return state.active() == null ? "" : state.active().name().toLowerCase(Locale.ROOT);
    }
}
