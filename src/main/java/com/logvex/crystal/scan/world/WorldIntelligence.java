package com.logvex.crystal.scan.world;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The world-intelligence composition root: the single object that owns the whole subsystem.
 *
 * <p>It brings together the exploration {@link WorldScanner}, the {@link DiscoveryDatabase},
 * the {@link RegionTracker}, the {@link DiscoveryClusterer}, the {@link BaritoneController} and
 * the {@link WorldIntelligenceAi} behind one API, so the module, HUD, dashboard and commands all
 * talk to this and never re-implement each other's job. Everything here is Minecraft-free except
 * through the interfaces the scanner already uses, so the whole subsystem is unit testable.
 *
 * <p><b>Responsibility split.</b> {@code WorldScanner} drives one scan run; this class owns the
 * data that outlives a run (the discovery database, region progress, AI history) and the
 * cross-cutting concerns (movement arbitration, persistence, cluster-based route targets).
 *
 * <p><b>Threading.</b> {@link #tick} and the mutation methods run on the client thread. The only
 * work off-thread is the scanner's classification and an optional model call, both of which hand
 * results back through callbacks or queues. Persistence is synchronous but bounded and only
 * happens on explicit save or a session end, never every tick.
 */
public final class WorldIntelligence implements AutoCloseable {

    /** The movement-claim owner name used for autonomous exploration. */
    public static final String MOVEMENT_OWNER = "world-intelligence";

    private final WorldScanner scanner;
    private final DiscoveryDatabase database;
    private final RegionTracker regions = new RegionTracker();
    private final DiscoveryClusterer clusterer = new DiscoveryClusterer();
    private final ChunkAnalyzer analyzer = new ChunkAnalyzer();
    private final MovementCoordinator movement;
    private final BaritoneController baritone;
    private final WorldIntelligenceAi ai;

    private String currentSessionId = "";
    private long lastSaveAt;
    private int observationsThisSession;
    private int newThisSession;
    private int lastRouteIndex = -1;
    private boolean autonomous = true;

    public WorldIntelligence(WorldScanner scanner, DiscoveryDatabase database,
                             MovementCoordinator movement, BaritoneController baritone,
                             WorldIntelligenceAi ai) {
        this.scanner = scanner;
        this.database = database;
        this.movement = movement;
        this.baritone = baritone;
        this.ai = ai;
    }

    // ------------------------------------------------------------------ accessors

    public WorldScanner scanner() {
        return scanner;
    }

    public DiscoveryDatabase database() {
        return database;
    }

    public RegionTracker regions() {
        return regions;
    }

    public DiscoveryClusterer clusterer() {
        return clusterer;
    }

    public MovementCoordinator movement() {
        return movement;
    }

    public BaritoneController baritone() {
        return baritone;
    }

    public WorldIntelligenceAi ai() {
        return ai;
    }

    /** How many observations have been recorded in the current session. */
    public int observationsThisSession() {
        return observationsThisSession;
    }

    /** How many observations in the current session were new (not updates of a known chunk). */
    public int newThisSession() {
        return newThisSession;
    }

    // ------------------------------------------------------------------ lifecycle

    /**
     * Starts a scan, claiming movement for autonomous exploration first so nothing else can drive
     * the player at the same time.
     *
     * @return {@code null} on success, or a user-facing reason why the scan did not start
     */
    public String start(long nowMillis) {
        return start(nowMillis, true);
    }

    /**
     * Starts a scan.
     *
     * @param autonomous when true the scanner claims movement and drives the player between
     *                   waypoints; when false it only samples chunks around the player and never
     *                   moves, so it can never fight another module for control
     * @return {@code null} on success, or a user-facing reason why the scan did not start
     */
    public String start(long nowMillis, boolean autonomous) {
        this.autonomous = autonomous;
        if (autonomous) {
            String conflict = movement.claim(MOVEMENT_OWNER, "world scan", nowMillis);
            if (conflict != null) {
                return "movement is in use by " + conflict;
            }
        }
        String error = scanner.start(nowMillis);
        if (error != null) {
            movement.release(MOVEMENT_OWNER);
            return error;
        }
        ScanSession session = scanner.session();
        currentSessionId = session == null ? "" : session.id();
        observationsThisSession = 0;
        newThisSession = 0;
        clusterer.reset();
        return null;
    }

    /** True when the current run is driving the player (as opposed to sampling in place). */
    public boolean autonomous() {
        return autonomous;
    }

    /** Pauses the scan and Baritone, releasing the movement claim while paused. */
    public void pause(long nowMillis) {
        scanner.pause(nowMillis);
        baritone.pause();
        movement.release(MOVEMENT_OWNER);
    }

    /** Resumes the scan, re-claiming movement and resuming Baritone. */
    public String resume(long nowMillis) {
        if (scanner.state() != ScanState.PAUSED) {
            return "not paused";
        }
        String conflict = movement.claim(MOVEMENT_OWNER, "world scan", nowMillis);
        if (conflict != null) {
            return "movement is in use by " + conflict;
        }
        scanner.resume(nowMillis);
        baritone.resume();
        return null;
    }

    /** Stops the scan, releases movement and Baritone, and saves the database. */
    public void stop(long nowMillis) {
        scanner.stop(nowMillis, ScanState.IDLE);
        baritone.cancel();
        movement.release(MOVEMENT_OWNER);
        save();
    }

    /** Cancels the scan and clears the map, regions, clusterer and database. */
    public void reset(long nowMillis) {
        scanner.reset(nowMillis);
        baritone.cancel();
        movement.release(MOVEMENT_OWNER);
        regions.clear();
        clusterer.reset();
        database.clear();
        analyzer.reset();
        currentSessionId = "";
        observationsThisSession = 0;
        newThisSession = 0;
        save();
    }

    /**
     * Advances the intelligence layer by one tick: drains completed classifications into the
     * database and region tracker, and records a route entry when the agent reaches a waypoint.
     * Must be called on the client thread.
     */
    public void tick(long nowMillis) {
        if (!scanner.isRunning()) {
            return;
        }
        drainObservations(nowMillis);
        recordRouteProgress(nowMillis);
    }

    /** Called on disconnect: stop cleanly and release every claim. */
    public void onDisconnect(long nowMillis) {
        scanner.stop(nowMillis, ScanState.IDLE);
        baritone.cancel();
        movement.releaseAll();
        save();
    }

    /** Turns a raw classified chunk into a verified observation and stores it. */
    private void drainObservations(long nowMillis) {
        List<WorldScanner.Classified> pending = scanner.drainPendingSamples();
        if (pending.isEmpty()) {
            return;
        }
        for (WorldScanner.Classified classified : pending) {
            ChunkSample sample = classified.sample();
            Observation observation = analyzer.analyze(currentSessionId, sample,
                    classified.classification(), nowMillis);
            if (observation == null) {
                continue;
            }
            boolean isNew = !database.contains(sample.chunkX(), sample.chunkZ());
            database.add(observation);
            regions.observe(sample.dimension(), sample.chunkX(), sample.chunkZ());
            observationsThisSession++;
            if (isNew) {
                newThisSession++;
                clusterer.noteProgress();
            }
        }
    }

    private void recordRouteProgress(long nowMillis) {
        int index = scanner.agent().routeIndex();
        int previous = lastRouteIndex;
        if (index != previous) {
            lastRouteIndex = index;
            List<RoutePlanner.Waypoint> route = scanner.route();
            if (previous >= 0 && previous < route.size()) {
                RoutePlanner.Waypoint reached = route.get(previous);
                database.addRoute(new DiscoveryDatabase.RouteEntry(scanner.session() == null ? ""
                        : scanner.session().dimension(), reached.x(), reached.z(), nowMillis,
                        "reached waypoint " + (previous + 1) + "/" + route.size()));
            }
        }
    }

    // ------------------------------------------------------------------ discovery queries

    /** The dimension the scanner is currently observing, or the last known one. */
    public String dimension() {
        return scanner.currentDimension();
    }

    /** Observations in the current dimension, newest first. */
    public List<Observation> recentObservations(int limit) {
        return database.recent(limit);
    }

    /** Ranked, de-duplicated exploration targets built from the recorded discoveries. */
    public List<DiscoveryClusterer.Cluster> buildTargets(int fromBlockX, int fromBlockZ, int maxTargets) {
        String dimension = dimension();
        Set<Long> positions = new LinkedHashSet<>();
        Set<Long> notable = new LinkedHashSet<>();
        for (Observation observation : database.discoveries(dimension)) {
            long key = ChunkKey.of(observation.chunkX(), observation.chunkZ());
            positions.add(key);
            if (observation.category() != StructureCategory.NATURAL
                    && observation.category() != StructureCategory.OTHER) {
                notable.add(key);
            }
        }
        List<DiscoveryClusterer.Cluster> targets =
                clusterer.buildTargets(positions, notable, 4, fromBlockX, fromBlockZ, maxTargets);
        if (targets.isEmpty() && clusterer.millisSinceProgress() > DiscoveryClusterer.FRONTIER_AFTER_MILLIS) {
            DiscoveryClusterer.Cluster frontier = clusterer.frontier(positions,
                    scanner.agent().bounds(), fromBlockX, fromBlockZ);
            if (frontier != null) {
                return List.of(frontier);
            }
        }
        return DiscoveryClusterer.tour(targets, fromBlockX, fromBlockZ);
    }

    // ------------------------------------------------------------------ AI

    /** Answers a natural-language question about the recorded data. */
    public void ask(String question, Consumer<WorldQuery.Answer> callback) {
        WorldQuery.Context context = new WorldQuery.Context(database, regions, dimension(),
                scanner.agent().bounds());
        ai.ask(question, context, callback);
    }

    /** Answers a question deterministically, with no model (used by the CLI and tests). */
    public WorldQuery.Answer askLocal(String question) {
        WorldQuery.Context context = new WorldQuery.Context(database, regions, dimension(),
                scanner.agent().bounds());
        return ai.askLocal(question, context);
    }

    /** Stores a summary of the current session in the database, deterministic or model-written. */
    public void recordSummary(String text, boolean fromModel, long nowMillis) {
        database.addSummary(new DiscoveryDatabase.AiSummary("session", dimension(), nowMillis, text,
                fromModel));
    }

    // ------------------------------------------------------------------ persistence

    /** Persists the discovery database. Best effort: a failure is returned, never thrown. */
    public String save() {
        try {
            database.save();
            lastSaveAt = System.currentTimeMillis();
            return null;
        } catch (IOException e) {
            return "could not save world intelligence: " + e.getMessage();
        }
    }

    /** Loads the discovery database from disk. Best effort. */
    public void load() {
        database.load();
        rebuildRegions();
    }

    /** Rebuilds region progress from the loaded observations, so progress survives a restart. */
    private void rebuildRegions() {
        regions.clear();
        for (Observation observation : database.observations()) {
            regions.observe(observation.dimension(), observation.chunkX(), observation.chunkZ());
        }
    }

    /** When the database was last written, or 0. */
    public long lastSaveAt() {
        return lastSaveAt;
    }

    @Override
    public void close() {
        movement.releaseAll();
        scanner.close();
    }
}
