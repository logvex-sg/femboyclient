package com.logvex.crystal.scan.world;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * The world scanner: the controller that ties the sampler, route planner, exploration agent,
 * flight controller, classifier, mapper and database together and drives one scan session.
 *
 * <p><b>Threading.</b> {@link #tick} and every state transition run on the client thread. The
 * only work that leaves the client thread is structure classification, which runs on a single
 * bounded daemon worker; results are handed back through a queue the client thread drains. That
 * is what keeps the mission's "never block the game thread with AI inference" rule: the client
 * thread only ever walks loaded chunks and does O(1) bookkeeping per chunk.
 *
 * <p><b>Safety.</b> The scanner reads only chunks the client already has loaded
 * ({@link WorldSampler#chunkLoaded}); it never forces a load. It stores only terrain/structure
 * classifications and, when explicitly enabled, the player's own position - never another
 * player's coordinates or inventory. It performs no network access: everything stays in the
 * local map and, on save, the local JSON database.
 *
 * <p><b>Cancellation.</b> A run is governed by a {@link CancellationToken}. {@link #stop}
 * cancels the token, releases flight and movement, and ends the session; classification already
 * queued is dropped at the next drain.
 */
public final class WorldScanner {

    /** How many classification tasks may be in flight before new work runs inline (backpressure). */
    private static final int MAX_IN_FLIGHT = 64;
    /** Consecutive stalled waypoint ticks before the scanner abandons a waypoint. */
    private static final int WAYPOINT_STALL_LIMIT = 200;
    /** Ticks with no new chunk observed before a route-less scan is considered exhausted. */
    private static final int IDLE_COMPLETION_TICKS = 200;
    /** Chunks sampled per tick, at most, regardless of the configured cap. */
    private static final int SAMPLE_WINDOW = 2;
    /** Chunk offsets of the sampling window, ordered nearest-to-player first. */
    private static final int[][] WINDOW_OFFSETS = windowOffsets();

    private static int[][] windowOffsets() {
        java.util.List<int[]> offsets = new java.util.ArrayList<>();
        for (int dz = -SAMPLE_WINDOW; dz <= SAMPLE_WINDOW; dz++) {
            for (int dx = -SAMPLE_WINDOW; dx <= SAMPLE_WINDOW; dx++) {
                offsets.add(new int[]{dx, dz});
            }
        }
        // Nearest first, so the per-tick budget is spent on the player's own chunk and its
        // immediate neighbours before it is wasted on distant chunks that are usually unloaded.
        offsets.sort(java.util.Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]));
        return offsets.toArray(new int[0][]);
    }

    private record ClassifiedResult(ChunkSample sample, Classification classification, long atMillis) {
    }

    /**
     * A classified chunk handed to the world-intelligence layer.
     *
     * <p>The classification computed here is passed on rather than recomputed downstream, so the
     * (possibly expensive, possibly hinted) classification happens exactly once per chunk.
     */
    public record Classified(ChunkSample sample, Classification classification) {
    }

    private final WorldSampler sampler;
    private final FlightPort flightPort;
    private final MovementPort movementPort;
    private final WorldMapper mapper;
    private final ScanDatabase database;

    private final ExecutorService classifierWorker;
    private final boolean ownsWorker;
    private final ConcurrentLinkedQueue<ClassifiedResult> completed = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Classified> intelligenceQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicBoolean closed = new AtomicBoolean();

    private final ExplorationAgent agent = new ExplorationAgent(8192);
    private final FlightController flight = new FlightController();

    private ScanConfig config = ScanConfig.defaults();
    private volatile ScanState state = ScanState.IDLE;
    private boolean autonomous = true;
    private CancellationToken token = new CancellationToken();
    private Function<ChunkSample, StructureCategory> hintSupplier;
    private List<RoutePlanner.Waypoint> route = List.of();

    private long startedAtMillis;
    private long tickCount;
    private long totalTickNanos;
    private int unloadedSkipped;
    private int lastProgressIndex = -1;
    private int idleTicks;
    private String message = "";

    /** Production constructor: owns a single low-priority daemon classifier thread. */
    public WorldScanner(WorldSampler sampler, FlightPort flightPort, MovementPort movementPort,
                        WorldMapper mapper, ScanDatabase database) {
        this(sampler, flightPort, movementPort, mapper, database,
                Executors.newSingleThreadExecutor(daemonFactory()), true);
    }

    /** Test/DI constructor: supply a synchronous or custom classifier executor. */
    public WorldScanner(WorldSampler sampler, FlightPort flightPort, MovementPort movementPort,
                        WorldMapper mapper, ScanDatabase database, ExecutorService worker, boolean ownsWorker) {
        this.sampler = sampler;
        this.flightPort = flightPort;
        this.movementPort = movementPort;
        this.mapper = mapper;
        this.database = database;
        this.classifierWorker = worker;
        this.ownsWorker = ownsWorker;
    }

    private static ThreadFactory daemonFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "logvex-world-scan-classify");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        };
    }

    /** Installs an optional external hint source (e.g. a local model). Deterministic without one. */
    public void setHintSupplier(Function<ChunkSample, StructureCategory> supplier) {
        this.hintSupplier = supplier;
    }

    public ScanState state() {
        return state;
    }

    public WorldMapper mapper() {
        return mapper;
    }

    public ScanDatabase database() {
        return database;
    }

    public ScanSession session() {
        return database.current();
    }

    public FlightController flight() {
        return flight;
    }

    public ExplorationAgent agent() {
        return agent;
    }

    public ScanConfig config() {
        return config;
    }

    public String message() {
        return message;
    }

    public boolean isRunning() {
        return state.running();
    }

    /**
     * Whether the run is allowed to drive the player. When false the scanner samples the chunks
     * around wherever the player already is and never calls the movement port, so it can never
     * fight another module for control. Set by {@link #start(long, boolean)}.
     */
    public boolean autonomous() {
        return autonomous;
    }

    /** Applies a new configuration snapshot; takes effect on the next tick, never mid-tick. */
    public void configure(ScanConfig newConfig) {
        if (newConfig != null) {
            this.config = newConfig;
        }
    }

    // ------------------------------------------------------------------ lifecycle

    /**
     * Starts a run. Fails (returning a reason) when no world is loaded; otherwise creates a
     * session, plans the route, engages flight when configured and moves to {@link ScanState#MAPPING}.
     */
    public String start(long nowMillis) {
        return start(nowMillis, true);
    }

    /**
     * Starts a run, optionally without autonomous movement. When {@code autonomous} is false the
     * scanner never calls the movement port and simply samples the chunks around the player, so a
     * user who does not want the client to drive them can still use the scanner.
     */
    public String start(long nowMillis, boolean autonomous) {
        this.autonomous = autonomous;
        if (state.running()) {
            return "already running";
        }
        if (sampler == null || !sampler.worldLoaded()) {
            state = ScanState.ERROR;
            message = "no world loaded";
            return message;
        }
        String dimension = sampler.dimension();
        ScanBounds bounds = ScanBounds.radius(sampler.playerBlockX() >> 4, sampler.playerBlockZ() >> 4,
                config.radiusChunks());
        route = RoutePlanner.plan(bounds, config.routeStepBlocks());
        database.begin(dimension, bounds, nowMillis);
        agent.begin(bounds, route, dimension, sampler.playerBlockX(), sampler.playerBlockZ(), 0);
        agent.reset();
        completed.clear();
        intelligenceQueue.clear();
        token = new CancellationToken("scan started");
        startedAtMillis = nowMillis;
        tickCount = 0;
        totalTickNanos = 0;
        unloadedSkipped = 0;
        lastProgressIndex = -1;
        idleTicks = 0;
        state = ScanState.INITIALIZING;
        message = "route of " + route.size() + " waypoints";

        if (autonomous && config.useFlight()) {
            FlightController.FlightResult result = flight.engage(flightPort, 20.0);
            if (!result.ok()) {
                message = result.message();
            }
        }
        state = ScanState.MAPPING;
        return null;
    }

    /** Pauses a running scan; the session and its progress are retained. */
    public void pause(long nowMillis) {
        if (!state.running() || state == ScanState.PAUSED) {
            return;
        }
        state = ScanState.PAUSED;
        message = "paused";
        ScanSession session = session();
        if (session != null) {
            session.setState(ScanState.PAUSED, nowMillis);
            session.setProgress(agent.routeIndex(), route.size(), nowMillis);
        }
        movementPort().stop();
    }

    /** Resumes a paused scan from where it left off. */
    public void resume(long nowMillis) {
        if (state != ScanState.PAUSED) {
            return;
        }
        state = ScanState.MAPPING;
        message = "resumed";
        ScanSession session = session();
        if (session != null) {
            session.setState(ScanState.MAPPING, nowMillis);
        }
        if (config.useFlight()) {
            flight.engage(flightPort, 20.0);
        }
    }

    /** Stops and cancels the run: releases flight and movement, ends the session. */
    public void stop(long nowMillis, ScanState finalState) {
        token.cancel("stopped by user");
        completed.clear();
        intelligenceQueue.clear();
        movementPort().stop();
        flight.release(flightPort);
        state = finalState == null ? ScanState.IDLE : finalState;
        message = finalState == ScanState.ERROR ? "stopped: error" : "stopped";
        ScanSession session = session();
        if (session != null) {
            session.setState(state, nowMillis);
            session.setProgress(agent.routeIndex(), route.size(), nowMillis);
        }
        database.end(state, nowMillis);
        agent.reset();
    }

    /** Cancels the run and clears the map and the in-memory session history. */
    public void reset(long nowMillis) {
        stop(nowMillis, ScanState.IDLE);
        mapper.clear();
        database.clearSessions();
        completed.clear();
        state = ScanState.IDLE;
        message = "reset";
    }

    /** Aborts with an error, releasing flight/movement and marking the session ERROR. */
    private void abort(String reason, long nowMillis) {
        token.cancel(reason);
        movementPort().stop();
        flight.release(flightPort);
        state = ScanState.ERROR;
        message = reason;
        ScanSession session = session();
        if (session != null) {
            session.setState(ScanState.ERROR, nowMillis);
        }
        database.end(ScanState.ERROR, nowMillis);
        agent.reset();
    }

    private void complete(long nowMillis) {
        movementPort().stop();
        flight.release(flightPort);
        state = ScanState.COMPLETED;
        message = "route complete";
        ScanSession session = session();
        if (session != null) {
            session.setState(ScanState.COMPLETED, nowMillis);
            session.setProgress(route.size(), route.size(), nowMillis);
        }
        database.end(ScanState.COMPLETED, nowMillis);
    }

    private MovementPort movementPort() {
        return movementPort == null ? NULL_MOVEMENT : movementPort;
    }

    private static final MovementPort NULL_MOVEMENT = new MovementPort() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public void goTo(int x, int y, int z) {
        }

        @Override
        public void stop() {
        }

        @Override
        public String source() {
            return "none";
        }
    };

    // ------------------------------------------------------------------ tick

    /**
     * Advances the scan by one tick. Must be called on the client thread. A no-op unless a run is
     * in progress. Never throws: a failure aborts the run cleanly rather than escaping the tick.
     */
    public void tick(long nowMillis) {
        if (!state.running() || state == ScanState.PAUSED || state == ScanState.INITIALIZING) {
            return;
        }
        long start = System.nanoTime();
        try {
            if (closed.get()) {
                return;
            }
            // Apply any classification the worker finished since the last tick.
            drainClassified(nowMillis);
            if (!sampler.worldLoaded()) {
                abort("world unloaded", nowMillis);
                return;
            }
            // A dimension change must abort before sampling, so nothing is ever recorded into a
            // dimension other than the one the session began in.
            ScanSession current = session();
            if (current != null && !current.dimension().equals(sampler.dimension())) {
                abort("dimension changed to " + sampler.dimension(), nowMillis);
                return;
            }
            agent.setPosition(sampler.playerBlockX(), sampler.playerBlockZ());
            // Sample the current window first, then ask the agent where to head next. Ordering the
            // sampling before the decision means the player's own chunk is never starved of the
            // per-tick budget by a move request.
            sampleBatch(nowMillis);

            ExplorationAgent.Decision decision = agent.tick(sampler.dimension(), config, true,
                    sampler.playerAlive());
            if (decision.abort()) {
                abort(decision.message(), nowMillis);
                return;
            }
            if (decision.finished()) {
                complete(nowMillis);
                return;
            }

            boolean moving = decision.moveToWaypoint() && autonomous && movementPort().available();
            if (moving) {
                movementPort().goTo(decision.waypointX(), sampler.playerBlockY(), decision.waypointZ());
            }
            if (config.useFlight()) {
                flight.maintain(flightPort, decision.waypointX(), decision.waypointZ(),
                        sampler.playerBlockX(), sampler.playerBlockZ());
            }

            if (agent.waypointStallTicks() > WAYPOINT_STALL_LIMIT) {
                // The waypoint is unreachable (a wall, a cliff, no movement source). Abandon it
                // rather than stalling the whole run, and release flight while we re-plan.
                flight.release(flightPort);
                agent.skipWaypoint();
            }

            // Safety net: if no new chunk has been observed for a while and the route is not
            // advancing, the region is exhausted - finish rather than idle forever.
            int index = agent.routeIndex();
            if (index == lastProgressIndex && !moving) {
                idleTicks++;
            } else {
                idleTicks = 0;
                lastProgressIndex = index;
            }
            if (idleTicks > IDLE_COMPLETION_TICKS) {
                complete(nowMillis);
                return;
            }

            ScanSession session = session();
            if (session != null) {
                session.setProgress(agent.routeIndex(), route.size(), nowMillis);
            }
            boolean analysing = pendingClassifications() > 0;
            if (moving) {
                state = ScanState.EXPLORING;
            } else if (analysing) {
                state = ScanState.ANALYZING;
            } else {
                state = ScanState.MAPPING;
            }
        } catch (Throwable t) {
            abort("scanner error: " + t.getClass().getSimpleName(), nowMillis);
        } finally {
            tickCount++;
            totalTickNanos += System.nanoTime() - start;
        }
    }

    /** Samples up to {@code config.maxChunksPerTick} unmapped chunks in a window around the player. */
    private void sampleBatch(long nowMillis) {
        int centerChunkX = sampler.playerBlockX() >> 4;
        int centerChunkZ = sampler.playerBlockZ() >> 4;
        int budget = config.maxChunksPerTick();
        for (int[] offset : WINDOW_OFFSETS) {
            if (budget <= 0) {
                break;
            }
            int chunkX = centerChunkX + offset[0];
            int chunkZ = centerChunkZ + offset[1];
            if (agent.alreadyVisited(chunkX, chunkZ) || mapper.contains(sampler.dimension(), chunkX, chunkZ)) {
                continue;
            }
            if (!sampler.chunkLoaded(chunkX, chunkZ)) {
                // Not loaded: do NOT mark it visited, so the scanner retries it once the player
                // moves close enough for the client to load it. Counted as skipped this tick.
                unloadedSkipped++;
                continue;
            }
            agent.markVisited(chunkX, chunkZ);
            budget--;
            ChunkSample sample = sampler.sample(chunkX, chunkZ, config, nowMillis);
            classify(sample, nowMillis);
        }
    }

    private void classify(ChunkSample sample, long nowMillis) {
        if (sample == null) {
            return;
        }
        if (!config.classifyOffThread() || classifierWorker == null || closed.get()
                || inFlight.get() >= MAX_IN_FLIGHT) {
            recordClassified(sample, StructureClassifier.classify(sample, hintFor(sample)), nowMillis);
            return;
        }
        inFlight.incrementAndGet();
        try {
            classifierWorker.execute(() -> {
                try {
                    Classification classification = StructureClassifier.classify(sample, hintFor(sample));
                    completed.add(new ClassifiedResult(sample, classification, nowMillis));
                } catch (Throwable ignored) {
                    // A malformed sample must never take down the worker thread.
                } finally {
                    inFlight.decrementAndGet();
                }
            });
        } catch (RejectedExecutionException e) {
            inFlight.decrementAndGet();
            recordClassified(sample, StructureClassifier.classify(sample, hintFor(sample)), nowMillis);
        }
    }

    /** The category hint supplied by an external source for a sample, or null. */
    public StructureCategory hintFor(ChunkSample sample) {
        Function<ChunkSample, StructureCategory> supplier = hintSupplier;
        if (supplier == null) {
            return null;
        }
        try {
            return supplier.apply(sample);
        } catch (Throwable t) {
            return null;
        }
    }

    private void drainClassified(long nowMillis) {
        ClassifiedResult result;
        while ((result = completed.poll()) != null) {
            if (token.isCancelled()) {
                continue;
            }
            recordClassified(result.sample(), result.classification(), result.atMillis());
        }
    }

    private void recordClassified(ChunkSample sample, Classification classification, long nowMillis) {
        if (token.isCancelled()) {
            return;
        }
        VerificationStatus status = ObservationEngine.verify(sample, classification);
        MappedChunk mapped = mapper.record(sample, classification, status, nowMillis);
        ScanSession session = session();
        if (session != null && mapped != null) {
            session.recordChunk(mapped, nowMillis);
        }
        // Hand the classified chunk to the intelligence layer, bounded so a long run cannot grow
        // memory without limit if the consumer stalls.
        if (intelligenceQueue.size() < MAX_IN_FLIGHT * 8) {
            intelligenceQueue.add(new Classified(sample, classification));
        }
    }

    /** Drains the classified chunks awaiting the intelligence layer. Called on the client thread. */
    public java.util.List<Classified> drainPendingSamples() {
        java.util.List<Classified> drained = new java.util.ArrayList<>();
        Classified next;
        while ((next = intelligenceQueue.poll()) != null) {
            drained.add(next);
        }
        return drained;
    }

    // ------------------------------------------------------------------ queries

    /** The number of classifications still queued or running on the worker. */
    public int pendingClassifications() {
        return inFlight.get() + completed.size();
    }

    /** Applies a user correction to a chunk in the map and the current session. */
    public boolean correct(int chunkX, int chunkZ, StructureCategory category, long nowMillis) {
        String dimension = sampler == null ? null : sampler.dimension();
        if (dimension == null) {
            return false;
        }
        boolean applied = mapper.correct(dimension, chunkX, chunkZ, category, nowMillis);
        ScanSession session = session();
        if (session != null) {
            session.correct(chunkX, chunkZ, category, nowMillis);
        }
        return applied;
    }

    /** An immutable view for the HUD and dashboard. */
    public ScanSnapshot snapshot() {
        ScanSession session = session();
        String dimension = sampler == null ? "" : sampler.dimension();
        int explored = session != null ? session.chunkCount() : mapper.size(dimension);
        int discoveries = session != null ? session.discoveryCount() : mapper.discoveryCount(dimension);
        long duration = session != null ? session.durationMillis() : 0;
        int index = agent.routeIndex();
        int size = route.size();
        double samplesPerSecond = tickCount == 0 ? 0
                : mapper.totalSize() / Math.max(0.001, tickCount / 20.0);
        long avgNanos = tickCount == 0 ? 0 : totalTickNanos / tickCount;
        return new ScanSnapshot(state, dimension, explored, discoveries, duration, index, size,
                ScanSnapshot.progress(index, size), pendingClassifications(), unloadedSkipped,
                session != null ? session.duplicates() : 0,
                flight.statusLine(flightPort), movementPort().source(), samplesPerSecond, avgNanos, message);
    }

    /** Releases the classifier worker. Safe to call more than once. */
    public void close() {
        if (closed.compareAndSet(false, true)) {
            token.cancel("scanner closed");
            if (ownsWorker && classifierWorker != null) {
                classifierWorker.shutdownNow();
            }
        }
    }

    /** The dimension the scanner is observing, or the last one it observed. */
    public String currentDimension() {
        ScanSession session = session();
        if (session != null) {
            return session.dimension();
        }
        return sampler == null ? "" : sampler.dimension();
    }

    /** Convenience for tests and diagnostics: the planned route size. */
    public int routeSize() {
        return route.size();
    }

    /** Convenience for tests: the planned route (immutable copy). */
    public List<RoutePlanner.Waypoint> route() {
        return new ArrayList<>(route);
    }
}
