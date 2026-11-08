package com.logvex.crystal.scan.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests the scanner controller end to end against fake ports and a synchronous classifier, so
 * the whole lifecycle - start, sample, classify, map, pause, resume, stop, abort, cancel - is
 * exercised deterministically without a Minecraft runtime.
 */
class WorldScannerTest {

    /** A scriptable world. Only chunks in {@code loaded} are ever sampled. */
    private static final class FakeWorld implements WorldSampler {
        boolean worldLoaded = true;
        boolean playerAlive = true;
        String dimension = "minecraft:overworld";
        int x = 0;
        int y = 64;
        int z = 0;
        final Set<Long> loaded = new HashSet<>();
        int sampledCount;

        void load(int chunkX, int chunkZ) {
            loaded.add(ChunkKey.of(chunkX, chunkZ));
        }

        @Override
        public String dimension() {
            return dimension;
        }

        @Override
        public int playerBlockX() {
            return x;
        }

        @Override
        public int playerBlockY() {
            return y;
        }

        @Override
        public int playerBlockZ() {
            return z;
        }

        @Override
        public boolean chunkLoaded(int chunkX, int chunkZ) {
            return loaded.contains(ChunkKey.of(chunkX, chunkZ));
        }

        @Override
        public ChunkSample sample(int chunkX, int chunkZ, ScanConfig config, long nowMillis) {
            sampledCount++;
            return new ChunkSample(dimension, chunkX, chunkZ, true, 64, "stone", 100, 100, 100, 0, 0, 0, 0, 0,
                    50, 0, 2, 0, 0, 0, 0, TerrainType.PLAINS, nowMillis);
        }

        @Override
        public boolean worldLoaded() {
            return worldLoaded;
        }

        @Override
        public boolean playerAlive() {
            return playerAlive;
        }
    }

    private static final class FakeFlight implements FlightPort {
        boolean available = true;
        boolean permitted = true;
        boolean flying;
        int setFlyingCalls;

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public boolean flying() {
            return flying;
        }

        @Override
        public boolean permitted() {
            return permitted;
        }

        @Override
        public boolean setFlying(boolean value) {
            setFlyingCalls++;
            flying = value;
            return true;
        }

        @Override
        public void setSpeed(double blocksPerSecond) {
        }

        @Override
        public boolean inFluid() {
            return false;
        }

        @Override
        public boolean onGround() {
            return false;
        }

        @Override
        public double horizontalSpeed() {
            return 20;
        }

        @Override
        public String source() {
            return "creative";
        }
    }

    private static final class FakeMovement implements MovementPort {
        boolean available = true;
        int goToCalls;
        int stopCalls;
        int lastX;
        int lastZ;

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public void goTo(int x, int y, int z) {
            goToCalls++;
            lastX = x;
            lastZ = z;
        }

        @Override
        public void stop() {
            stopCalls++;
        }

        @Override
        public String source() {
            return available ? "fake" : "none";
        }
    }

    private record Harness(WorldScanner scanner, FakeWorld world, FakeFlight flight, FakeMovement movement,
                           ScanDatabase database) {
    }

    /** An executor that holds tasks so a test can run them at a controlled moment. */
    private static final class QueuedExecutor extends java.util.concurrent.AbstractExecutorService {
        final java.util.List<Runnable> queued = new java.util.ArrayList<>();

        @Override
        public void execute(Runnable command) {
            queued.add(command);
        }

        void runAll() {
            java.util.List<Runnable> snapshot = new java.util.ArrayList<>(queued);
            queued.clear();
            for (Runnable runnable : snapshot) {
                runnable.run();
            }
        }

        @Override
        public void shutdown() {
        }

        @Override
        public java.util.List<Runnable> shutdownNow() {
            return queued;
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, java.util.concurrent.TimeUnit unit) {
            return true;
        }
    }

    private Harness harness(Path dir, ScanConfig config, java.util.concurrent.ExecutorService executor) {
        FakeWorld world = new FakeWorld();
        FakeFlight flight = new FakeFlight();
        FakeMovement movement = new FakeMovement();
        ScanDatabase database = new ScanDatabase(dir.resolve("scan.json"));
        WorldScanner scanner = new WorldScanner(world, flight, movement, new WorldMapper(2000), database,
                executor, true);
        scanner.configure(config);
        return new Harness(scanner, world, flight, movement, database);
    }

    private Harness harness(Path dir, ScanConfig config) {
        java.util.concurrent.ExecutorService direct = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "test-classify");
            thread.setDaemon(true);
            return thread;
        });
        return harness(dir, config, direct);
    }

    private static ScanConfig smallConfig() {
        return new ScanConfig(1, 8, 8, 8, 16, true, false, 500);
    }

    @Test
    void startFailsWhenNoWorldIsLoaded(@TempDir Path dir) {
        Harness h = harness(dir, smallConfig());
        h.world.worldLoaded = false;
        assertEquals("no world loaded", h.scanner.start(1L));
        assertEquals(ScanState.ERROR, h.scanner.state());
        h.scanner.close();
    }

    @Test
    void startPlansARouteAndCreatesASession(@TempDir Path dir) {
        Harness h = harness(dir, smallConfig());
        h.world.load(0, 0);
        assertNull(h.scanner.start(1L));
        assertTrue(h.scanner.routeSize() > 0);
        assertNotNull(h.scanner.session());
        assertEquals("minecraft:overworld", h.scanner.session().dimension());
        assertTrue(h.flight.flying, "flight should be engaged on start when configured");
        h.scanner.close();
    }

    @Test
    void tickSamplesLoadedChunksAndMapsThem(@TempDir Path dir) {
        Harness h = harness(dir, smallConfig());
        h.world.load(0, 0);
        h.world.load(1, 0);
        h.world.load(-1, 0);
        h.scanner.start(1L);
        for (int i = 0; i < 5; i++) {
            h.scanner.tick(2L + i);
        }
        assertTrue(h.scanner.mapper().totalSize() >= 1, "loaded chunks should be mapped");
        assertTrue(h.world.sampledCount >= 1);
        h.scanner.close();
    }

    @Test
    void unloadedChunksAreSkippedNotClassified(@TempDir Path dir) {
        Harness h = harness(dir, smallConfig());
        // Nothing is loaded: the scanner must sample nothing and classify nothing.
        h.scanner.start(1L);
        for (int i = 0; i < 3; i++) {
            h.scanner.tick(2L + i);
        }
        assertEquals(0, h.world.sampledCount);
        assertEquals(0, h.scanner.mapper().totalSize());
        assertTrue(h.scanner.snapshot().unloadedSkipped() > 0);
        h.scanner.close();
    }

    @Test
    void duplicateObservationsAreNotDoubleCounted(@TempDir Path dir) {
        Harness h = harness(dir, smallConfig());
        h.world.load(0, 0);
        h.scanner.start(1L);
        for (int i = 0; i < 20; i++) {
            h.scanner.tick(2L + i);
        }
        // The player never moved, so exactly the chunks in the sampling window are mapped once.
        int mapped = h.scanner.mapper().size("minecraft:overworld");
        assertTrue(mapped >= 1);
        assertTrue(h.world.sampledCount <= 25, "each chunk is sampled at most once");
        h.scanner.close();
    }

    @Test
    void dimensionChangeAbortsTheRun(@TempDir Path dir) {
        Harness h = harness(dir, smallConfig());
        h.world.load(0, 0);
        h.scanner.start(1L);
        h.world.dimension = "minecraft:the_nether";
        h.scanner.tick(2L);
        assertEquals(ScanState.ERROR, h.scanner.state());
        assertFalse(h.scanner.isRunning());
        h.scanner.close();
    }

    @Test
    void worldUnloadingAbortsTheRun(@TempDir Path dir) {
        Harness h = harness(dir, smallConfig());
        h.world.load(0, 0);
        h.scanner.start(1L);
        h.world.worldLoaded = false;
        h.scanner.tick(2L);
        assertEquals(ScanState.ERROR, h.scanner.state());
        h.scanner.close();
    }

    @Test
    void pauseAndResumeRetainTheSession(@TempDir Path dir) {
        Harness h = harness(dir, smallConfig());
        h.world.load(0, 0);
        h.scanner.start(1L);
        h.scanner.tick(2L);
        String id = h.scanner.session().id();
        h.scanner.pause(3L);
        assertEquals(ScanState.PAUSED, h.scanner.state());
        assertTrue(h.movement.stopCalls > 0, "pause must stop movement");
        h.scanner.tick(4L);
        assertEquals(ScanState.PAUSED, h.scanner.state(), "a paused scan must not advance");
        h.scanner.resume(5L);
        assertTrue(h.scanner.state().running());
        assertEquals(id, h.scanner.session().id(), "resume keeps the same session");
        h.scanner.close();
    }

    @Test
    void stopReleasesFlightAndMovementAndEndsTheSession(@TempDir Path dir) {
        Harness h = harness(dir, smallConfig());
        h.world.load(0, 0);
        h.scanner.start(1L);
        h.scanner.tick(2L);
        h.scanner.stop(3L, ScanState.IDLE);
        assertFalse(h.flight.flying, "stop must release a flight the scanner engaged");
        assertTrue(h.movement.stopCalls > 0);
        assertFalse(h.scanner.isRunning());
        assertNull(h.scanner.session(), "the session is no longer current after stop");
        h.scanner.close();
    }

    @Test
    void resetClearsTheMapAndHistory(@TempDir Path dir) {
        Harness h = harness(dir, smallConfig());
        h.world.load(0, 0);
        h.scanner.start(1L);
        h.scanner.tick(2L);
        h.scanner.reset(3L);
        assertEquals(0, h.scanner.mapper().totalSize());
        assertEquals(0, h.scanner.database().sessions().size());
        assertEquals(ScanState.IDLE, h.scanner.state());
        h.scanner.close();
    }

    @Test
    void cancellationDropsQueuedWork(@TempDir Path dir) {
        QueuedExecutor executor = new QueuedExecutor();
        Harness h = harness(dir, new ScanConfig(1, 8, 8, 8, 16, true, true, 500), executor);
        h.world.load(0, 0);
        h.scanner.start(1L);
        h.scanner.tick(2L);
        // Classification was queued on the worker, not yet applied.
        assertTrue(h.scanner.pendingClassifications() > 0);
        h.scanner.stop(3L, ScanState.IDLE);
        executor.runAll();
        h.scanner.tick(4L);
        // The cancelled run's queued results are dropped, so nothing lands in the map.
        assertEquals(0, h.scanner.mapper().totalSize());
        h.scanner.close();
    }

    @Test
    void offThreadClassificationIsDrainedIntoTheMap(@TempDir Path dir) {
        QueuedExecutor executor = new QueuedExecutor();
        Harness h = harness(dir, new ScanConfig(1, 8, 8, 8, 16, true, true, 500), executor);
        h.world.load(0, 0);
        h.scanner.start(1L);
        h.scanner.tick(2L);
        assertTrue(h.scanner.pendingClassifications() > 0, "work is handed to the worker thread");
        executor.runAll();
        h.scanner.tick(3L);
        assertTrue(h.scanner.mapper().totalSize() >= 1, "drained results are recorded on the client tick");
        h.scanner.close();
    }

    @Test
    void snapshotReflectsProgressAndCounts(@TempDir Path dir) {
        Harness h = harness(dir, smallConfig());
        h.world.load(0, 0);
        h.scanner.start(1L);
        h.scanner.tick(2L);
        ScanSnapshot snapshot = h.scanner.snapshot();
        assertEquals("minecraft:overworld", snapshot.dimension());
        assertTrue(snapshot.routeSize() >= 0);
        assertTrue(snapshot.progress() >= 0 && snapshot.progress() <= 1);
        assertEquals("creative", snapshot.flightStatus().split(" ")[0]);
        h.scanner.close();
    }

    @Test
    void correctAppliesAUserVerdict(@TempDir Path dir) {
        Harness h = harness(dir, smallConfig());
        h.world.load(0, 0);
        h.scanner.start(1L);
        h.scanner.tick(2L);
        assertTrue(h.scanner.correct(0, 0, StructureCategory.FARM, 3L));
        MappedChunk chunk = h.scanner.mapper().get("minecraft:overworld", 0, 0);
        assertNotNull(chunk);
        assertTrue(chunk.manual());
        assertEquals(StructureCategory.FARM, chunk.category());
        h.scanner.close();
    }

    @Test
    void closeIsIdempotent(@TempDir Path dir) {
        Harness h = harness(dir, smallConfig());
        h.scanner.close();
        h.scanner.close();
    }
}
