package com.logvex.crystal.scan.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.logvex.crystal.integration.baritone.BaritoneService;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests the world-intelligence composition root end to end: lifecycle, movement arbitration,
 * observation draining into the database, region progress, cluster targets and persistence.
 */
class WorldIntelligenceTest {

    /** A world where every chunk near the origin is loaded and full of storage blocks. */
    private static final class FakeWorld implements WorldSampler {
        String dimension = "minecraft:overworld";
        int x = 0;
        int y = 64;
        int z = 0;
        final Set<Long> loaded = new HashSet<>();

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
            if (!chunkLoaded(chunkX, chunkZ)) {
                return ChunkSample.unloaded(dimension, chunkX, chunkZ, nowMillis);
            }
            // Dense building + several chests: a confident, self-evident storage finding.
            return new ChunkSample(dimension, chunkX, chunkZ, true, 64, "stone", 100, 60, 40, 0, 0, 0, 0, 0,
                    40, 0, 6, 5, 0, 0, 0, 0, TerrainType.PLAINS, false, 500L, true, nowMillis);
        }

        @Override
        public boolean worldLoaded() {
            return true;
        }

        @Override
        public boolean playerAlive() {
            return true;
        }
    }

    private static final class FakeFlight implements FlightPort {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public boolean flying() {
            return false;
        }

        @Override
        public boolean permitted() {
            return false;
        }

        @Override
        public boolean setFlying(boolean flying) {
            return false;
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
            return true;
        }

        @Override
        public double horizontalSpeed() {
            return 0;
        }

        @Override
        public String source() {
            return "none";
        }
    }

    private static final class FakeMovement implements MovementPort {
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
    }

    private record Harness(WorldIntelligence intelligence, FakeWorld world, MovementCoordinator movement,
                           DiscoveryDatabase database, WorldScanner scanner) {
    }

    private Harness harness(Path dir) {
        FakeWorld world = new FakeWorld();
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                world.loaded.add(ChunkKey.of(x, z));
            }
        }
        ScanDatabase sessions = new ScanDatabase(dir.resolve("scan.json"));
        WorldScanner scanner = new WorldScanner(world, new FakeFlight(), new FakeMovement(),
                new WorldMapper(4000), sessions);
        MovementCoordinator movement = new MovementCoordinator();
        DiscoveryDatabase database = new DiscoveryDatabase(dir.resolve("world-intelligence.json"));
        BaritoneController baritone = new BaritoneController(new BaritoneService(), movement,
                WorldIntelligence.MOVEMENT_OWNER);
        WorldIntelligence intelligence = new WorldIntelligence(scanner, database, movement, baritone,
                new WorldIntelligenceAi(null));
        return new Harness(intelligence, world, movement, database, scanner);
    }

    @Test
    void startClaimsMovementAndStopReleasesIt(@TempDir Path dir) {
        Harness h = harness(dir);
        assertNull(h.intelligence().start(1000L));
        assertTrue(h.movement().holds(WorldIntelligence.MOVEMENT_OWNER));
        assertTrue(h.scanner().isRunning());
        h.intelligence().stop(2000L);
        assertTrue(h.movement().free());
        assertFalse(h.scanner().isRunning());
    }

    @Test
    void startRefusedWhenMovementIsHeldByAnother(@TempDir Path dir) {
        Harness h = harness(dir);
        h.movement().claim("killaura", "pvp", 1L);
        String error = h.intelligence().start(1000L);
        assertNotNull(error);
        assertTrue(error.contains("killaura"));
        assertFalse(h.scanner().isRunning());
    }

    @Test
    void tickRecordsVerifiedObservationsIntoTheDatabase(@TempDir Path dir) {
        Harness h = harness(dir);
        h.intelligence().start(1000L);
        for (int i = 0; i < 40 && h.database().size() == 0; i++) {
            h.scanner().tick(1000L + i * 50L);
            h.intelligence().tick(1000L + i * 50L);
        }
        assertTrue(h.database().size() > 0, "expected observations to be recorded");
        Observation first = h.database().observations().get(0);
        assertEquals(StructureCategory.STORAGE, first.category());
        assertEquals(VerificationStatus.CONFIRMED, first.verification());
        assertTrue(first.discovered());
        assertTrue(h.intelligence().regions().visitedChunks("minecraft:overworld") > 0);
        h.intelligence().stop(5000L);
    }

    @Test
    void pauseReleasesMovementAndResumeReclaimsIt(@TempDir Path dir) {
        Harness h = harness(dir);
        h.intelligence().start(1000L);
        h.intelligence().pause(1500L);
        assertEquals(ScanState.PAUSED, h.scanner().state());
        assertTrue(h.movement().free());
        assertNull(h.intelligence().resume(1600L));
        assertTrue(h.movement().holds(WorldIntelligence.MOVEMENT_OWNER));
        h.intelligence().stop(2000L);
    }

    @Test
    void resetClearsEverything(@TempDir Path dir) {
        Harness h = harness(dir);
        h.intelligence().start(1000L);
        for (int i = 0; i < 40 && h.database().size() == 0; i++) {
            h.scanner().tick(1000L + i * 50L);
            h.intelligence().tick(1000L + i * 50L);
        }
        h.intelligence().reset(3000L);
        assertEquals(0, h.database().size());
        assertTrue(h.movement().free());
        assertEquals(0, h.intelligence().regions().visitedChunks("minecraft:overworld"));
    }

    @Test
    void persistenceSurvivesReload(@TempDir Path dir) {
        Harness h = harness(dir);
        h.intelligence().start(1000L);
        for (int i = 0; i < 40 && h.database().size() == 0; i++) {
            h.scanner().tick(1000L + i * 50L);
            h.intelligence().tick(1000L + i * 50L);
        }
        int recorded = h.database().size();
        h.intelligence().stop(5000L);

        DiscoveryDatabase reloaded = new DiscoveryDatabase(dir.resolve("world-intelligence.json"));
        reloaded.load();
        assertEquals(recorded, reloaded.size());
    }

    @Test
    void clusterTargetsAreBuiltFromDiscoveries(@TempDir Path dir) {
        Harness h = harness(dir);
        h.database().add(new Observation("a", "s", "minecraft:overworld", 0, 0, 1L,
                StructureCategory.STORAGE, 0.9, VerificationStatus.CONFIRMED, java.util.List.of(), null, ""));
        h.database().add(new Observation("b", "s", "minecraft:overworld", 1, 0, 1L,
                StructureCategory.STORAGE, 0.9, VerificationStatus.CONFIRMED, java.util.List.of(), null, ""));
        var targets = h.intelligence().buildTargets(0, 0, 10);
        assertFalse(targets.isEmpty());
        assertEquals(2, targets.get(0).count());
    }

    @Test
    void baritoneControllerReportsUnavailable() {
        Harness h = harness(Path.of(System.getProperty("java.io.tmpdir"), "wi-baritone"));
        assertFalse(h.intelligence().baritone().available());
        assertFalse(h.intelligence().baritone().statusLine().isBlank());
        assertFalse(h.intelligence().baritone().pathing());
        assertNotNull(h.intelligence().baritone().goTo(0, 64, 0));
    }

    @Test
    void askLocalWorksWithoutAModel(@TempDir Path dir) {
        Harness h = harness(dir);
        assertFalse(h.intelligence().ai().modelAvailable());
        var answer = h.intelligence().askLocal("how many discoveries are recorded");
        assertTrue(answer.supported());
    }
}
