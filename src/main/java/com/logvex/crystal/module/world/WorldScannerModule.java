package com.logvex.crystal.module.world;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.config.ConfigPaths;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.notification.NotificationLevel;
import com.logvex.crystal.scan.world.ScanBounds;
import com.logvex.crystal.scan.world.ScanConfig;
import com.logvex.crystal.scan.world.ScanDatabase;
import com.logvex.crystal.scan.world.ScanProfile;
import com.logvex.crystal.scan.world.ScanSnapshot;
import com.logvex.crystal.scan.world.ScanState;
import com.logvex.crystal.scan.world.StructureCategory;
import com.logvex.crystal.scan.world.WorldIntelligence;
import com.logvex.crystal.scan.world.WorldIntelligenceAi;
import com.logvex.crystal.scan.world.DiscoveryDatabase;
import com.logvex.crystal.scan.world.MovementCoordinator;
import com.logvex.crystal.scan.world.BaritoneController;
import com.logvex.crystal.scan.world.WorldMapper;
import com.logvex.crystal.scan.world.WorldScanner;
import com.logvex.crystal.scan.world.mc.BaritoneMovementPort;
import com.logvex.crystal.scan.world.mc.MinecraftFlightPort;
import com.logvex.crystal.scan.world.mc.MinecraftWorldSampler;
import com.logvex.crystal.scan.world.mc.MiniAiBackend;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.EnumSetting;
import com.logvex.crystal.setting.IntSetting;
import java.io.IOException;
import net.minecraft.client.MinecraftClient;

/**
 * WorldScanner: the in-game front end for the autonomous world scanner.
 *
 * <p>It owns the {@link WorldScanner} controller, applies the user's settings to it every tick,
 * drains completion notices and persists the session on stop/shutdown. All the scanning logic
 * lives in the Minecraft-free {@code scan.world} package; this module is the thin adapter that
 * gives it the live client (through {@link MinecraftWorldSampler}, {@link MinecraftFlightPort}
 * and {@link BaritoneMovementPort}) and the settings that drive it.
 *
 * <p>Everything is local and read-only with respect to the world: it reads loaded chunks, and
 * stores only terrain/structure classifications plus the player's own position when explicitly
 * enabled. It performs no network access.
 */
public final class WorldScannerModule extends Module {

    private final IntSetting radius = add("RadiusChunks", "Half-width of the scan area, in chunks.", 8, 1, 32);
    private final IntSetting verticalRange = add("VerticalRange", "Blocks above/below the player to sample.", 24, 4, 96);
    private final IntSetting sampleStep = add("SampleStep", "Blocks between sampled columns (larger = faster).", 4, 1, 16);
    private final IntSetting chunksPerTick = add("ChunksPerTick", "Chunks sampled per client tick.", 8, 1, 32);
    private final IntSetting routeStep = add("RouteStep", "Blocks between route waypoints.", 32, 8, 128);
    private final BooleanSetting useFlight = add("UseFlight", "Engage authorised flight to move between waypoints.", true);
    private final BooleanSetting offThread = add("OffThreadClassify", "Classify chunks on a background worker.", true);
    private final IntSetting observationCap = add("ObservationCap", "Maximum chunks retained per dimension.", 4000, 256, 20000);
    private final EnumSetting<Mode> mode = add("Mode", "How the scanner behaves when enabled.", Mode.MANUAL);
    private final BooleanSetting intelligence = add("WorldIntelligence",
            "Record verified discoveries, region progress and cluster targets in the discovery database.", true);
    private final BooleanSetting autonomousMovement = add("AutonomousMovement",
            "Let the scanner drive the player between waypoints (claims movement; never fights another module).",
            true);
    private final BooleanSetting modelSummaries = add("ModelSummaries",
            "Allow the optional local model to rephrase world-intelligence answers. Deterministic without it.",
            true);

    /** How the scanner behaves. */
    public enum Mode {
        /** Enabled = the module is on, but a scan only runs when the user starts it. */
        MANUAL,
        /** Enabled = a scan starts automatically once a world is loaded. */
        AUTO
    }

    private final WorldMapper mapper = new WorldMapper(4000);
    // Built lazily: constructing it needs the Fabric config dir, which is only available once
    // the game has launched, and the registry test constructs every module without a game.
    private ScanDatabase database;
    private WorldScanner scanner;
    private WorldIntelligence intelligenceLayer;
    private String lastStateMessage = "";
    private boolean started;

    public WorldScannerModule() {
        super(Category.WORLD, "WorldScanner",
                "Autonomous world mapping: route planning, structure classification and a persistent map.");
    }

    @Override
    protected void onEnable() {
        db().load();
        ensureScanner();
        if (intelligence.value()) {
            ensureIntelligence().load();
            setIntelligenceHudVisible(true);
        }
        if (mode.value() == Mode.AUTO) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.world != null) {
                start();
            }
        }
    }

    @Override
    protected void onDisable() {
        stop();
        saveDatabase();
        setIntelligenceHudVisible(false);
    }

    @Override
    public void onDisconnect() {
        // Stop the run but do not disable: the module resumes on the next world if in AUTO mode.
        if (intelligenceLayer != null) {
            intelligenceLayer.onDisconnect(System.currentTimeMillis());
        } else {
            stop();
        }
        started = false;
    }

    @Override
    protected void onTick() {
        if (scanner == null) {
            return;
        }
        scanner.configure(scanConfig());
        if (mode.value() == Mode.AUTO && !started && scanner.state() == ScanState.IDLE) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.world != null) {
                start();
            }
        }
        if (scanner.isRunning()) {
            scanner.tick(System.currentTimeMillis());
            if (intelligence.value() && intelligenceLayer != null) {
                intelligenceLayer.ai().setModelAllowed(modelSummaries.value());
                intelligenceLayer.tick(System.currentTimeMillis());
            }
        }
        String message = scanner.message();
        if (!message.isEmpty() && !message.equals(lastStateMessage)
                && (scanner.state() == ScanState.COMPLETED || scanner.state() == ScanState.ERROR)) {
            lastStateMessage = message;
            notifyUser("Scan " + scanner.state().label() + ": " + message,
                    scanner.state() == ScanState.ERROR ? NotificationLevel.WARN : NotificationLevel.SUCCESS);
            saveDatabase();
        }
    }

    @Override
    protected void onShutdown() {
        if (intelligenceLayer != null) {
            intelligenceLayer.save();
            intelligenceLayer.close();
        }
        if (scanner != null) {
            scanner.close();
        }
        saveDatabase();
    }

    private void ensureScanner() {
        if (scanner != null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        CrystalClient bootstrap = CrystalClient.get();
        var baritone = bootstrap == null ? null : bootstrap.baritone();
        scanner = new WorldScanner(new MinecraftWorldSampler(client), new MinecraftFlightPort(client),
                new BaritoneMovementPort(baritone), mapper, db());
        scanner.configure(scanConfig());
    }

    /**
     * Builds the world-intelligence layer lazily, so the registry test (which constructs every
     * module without a running game) never touches the config directory or a live client.
     */
    private WorldIntelligence ensureIntelligence() {
        if (intelligenceLayer != null) {
            return intelligenceLayer;
        }
        ensureScanner();
        CrystalClient bootstrap = CrystalClient.get();
        var baritoneService = bootstrap == null ? null : bootstrap.baritone();
        MovementCoordinator movement = new MovementCoordinator();
        BaritoneController baritone = new BaritoneController(baritoneService, movement,
                WorldIntelligence.MOVEMENT_OWNER);
        var aiManager = bootstrap == null ? null : bootstrap.ai();
        WorldIntelligenceAi ai = new WorldIntelligenceAi(aiManager == null ? null : new MiniAiBackend(aiManager));
        DiscoveryDatabase discoveryDb = new DiscoveryDatabase(ConfigPaths.worldIntelligence());
        intelligenceLayer = new WorldIntelligence(scanner, discoveryDb, movement, baritone, ai);
        return intelligenceLayer;
    }

    private ScanDatabase db() {
        if (database == null) {
            database = new ScanDatabase(ConfigPaths.scanSessions());
        }
        return database;
    }

    /** The current settings as an immutable scanner configuration snapshot. */
    public ScanConfig scanConfig() {
        return new ScanConfig(radius.value(), verticalRange.value(), sampleStep.value(), chunksPerTick.value(),
                routeStep.value(), useFlight.value(), offThread.value(), observationCap.value());
    }

    // ------------------------------------------------------------------ controls

    /** Starts a scan, reporting why it could not when the world is not ready. */
    public void start() {
        ensureScanner();
        boolean autonomous = autonomousMovement.value();
        String error;
        if (intelligence.value()) {
            error = ensureIntelligence().start(System.currentTimeMillis(), autonomous);
        } else {
            error = scanner.start(System.currentTimeMillis());
        }
        if (error != null) {
            notifyUser("Cannot start scan: " + error, NotificationLevel.WARN);
            return;
        }
        started = true;
        setHudVisible(true);
        notifyUser("Scan started: " + scanner.routeSize() + " waypoints", NotificationLevel.CUTE);
    }

    public void pause() {
        if (scanner == null) {
            return;
        }
        if (intelligenceLayer != null) {
            intelligenceLayer.pause(System.currentTimeMillis());
        } else {
            scanner.pause(System.currentTimeMillis());
        }
    }

    public void resume() {
        if (scanner == null) {
            return;
        }
        if (intelligenceLayer != null) {
            String error = intelligenceLayer.resume(System.currentTimeMillis());
            if (error != null) {
                notifyUser("Cannot resume: " + error, NotificationLevel.WARN);
            }
        } else {
            scanner.resume(System.currentTimeMillis());
        }
    }

    public void stop() {
        if (scanner == null || !scanner.isRunning()) {
            return;
        }
        if (intelligenceLayer != null) {
            intelligenceLayer.stop(System.currentTimeMillis());
        } else {
            scanner.stop(System.currentTimeMillis(), ScanState.IDLE);
        }
        started = false;
        setHudVisible(false);
        saveDatabase();
    }

    /** Cancels the run and clears the map and session history. */
    public void resetSession() {
        if (scanner == null) {
            return;
        }
        if (intelligenceLayer != null) {
            intelligenceLayer.reset(System.currentTimeMillis());
        } else {
            scanner.reset(System.currentTimeMillis());
        }
        started = false;
        setHudVisible(false);
        notifyUser("Scan session reset", NotificationLevel.INFO);
    }

    /** Shows or hides the scanner's HUD readout, if the HUD is available. */
    private void setHudVisible(boolean visible) {
        setElementVisible("scan", visible);
    }

    /** Shows or hides the world-intelligence HUD readout, if the HUD is available. */
    private void setIntelligenceHudVisible(boolean visible) {
        setElementVisible(com.logvex.crystal.gui.hud.IntelligenceHudElement.ID, visible);
    }

    private void setElementVisible(String id, boolean visible) {
        try {
            if (context() != null && context().hudManager() != null) {
                context().hudManager().setElementVisible(id, visible);
            }
        } catch (RuntimeException ignored) {
            // The HUD is optional in tests; a missing manager must not break the module.
        }
    }

    /** Applies a user correction to a chunk's classification. */
    public boolean correct(int chunkX, int chunkZ, StructureCategory category) {
        return scanner != null && scanner.correct(chunkX, chunkZ, category, System.currentTimeMillis());
    }

    // ------------------------------------------------------------------ accessors

    public WorldScanner scanner() {
        ensureScanner();
        return scanner;
    }

    public WorldMapper mapper() {
        return mapper;
    }

    /**
     * The world-intelligence layer, or {@code null} when the module has never been enabled. Only
     * built on demand so a headless registry test never touches the config directory.
     */
    public WorldIntelligence intelligence() {
        return intelligenceLayer;
    }

    /** Whether the intelligence layer is switched on in settings. */
    public boolean intelligenceEnabled() {
        return intelligence.value();
    }

    /** Whether the scanner is allowed to drive the player. */
    public boolean autonomousMovementEnabled() {
        return autonomousMovement.value();
    }

    /** Whether the optional local model may rephrase world-intelligence answers. */
    public boolean modelSummariesEnabled() {
        return modelSummaries.value();
    }

    public ScanDatabase database() {
        return db();
    }

    public ScanSnapshot snapshot() {
        return scanner == null ? null : scanner.snapshot();
    }

    public ScanState state() {
        return scanner == null ? ScanState.IDLE : scanner.state();
    }

    public boolean isScanning() {
        return scanner != null && scanner.isRunning();
    }

    public ScanBounds bounds() {
        return scanner == null ? null : scanner.agent().bounds();
    }

    // ------------------------------------------------------------------ profiles & persistence

    /** Saves the current settings as a named scan profile. */
    public void saveProfile(String name) {
        db().putProfile(new ScanProfile(name, scanConfig()));
        saveDatabase();
        notifyUser("Saved scan profile '" + name + "'", NotificationLevel.SUCCESS);
    }

    /** Applies a stored scan profile to the module's settings. */
    public boolean applyProfile(String name) {
        ScanProfile profile = db().profile(name);
        if (profile == null) {
            return false;
        }
        ScanConfig c = profile.config();
        radius.set(c.radiusChunks());
        verticalRange.set(c.verticalRange());
        sampleStep.set(c.sampleStep());
        chunksPerTick.set(c.maxChunksPerTick());
        routeStep.set(c.routeStepBlocks());
        useFlight.set(c.useFlight());
        offThread.set(c.classifyOffThread());
        observationCap.set(c.observationCap());
        if (scanner != null) {
            scanner.configure(scanConfig());
        }
        return true;
    }

    /** Persists the session database. Best effort: a failure is logged, never thrown into a tick. */
    public void saveDatabase() {
        try {
            db().save();
        } catch (IOException e) {
            reportErrorThrottled("could not save scan database", e, 30_000L);
        }
    }

    private void notifyUser(String message, NotificationLevel level) {
        CrystalClient bootstrap = CrystalClient.get();
        if (bootstrap != null) {
            bootstrap.notify(message, level);
        }
    }
}
