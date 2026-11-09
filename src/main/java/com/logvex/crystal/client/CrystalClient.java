package com.logvex.crystal.client;

import com.logvex.crystal.assets.AssetRegistry;
import com.logvex.crystal.combat.CombatService;
import com.logvex.crystal.command.CommandManager;
import com.logvex.crystal.config.AppearanceStore;
import com.logvex.crystal.config.AutoSave;
import com.logvex.crystal.config.ConfigIo;
import com.logvex.crystal.config.ConfigManager;
import com.logvex.crystal.config.ConfigPaths;
import com.logvex.crystal.config.FavoritesStore;
import com.logvex.crystal.config.HudLayoutStore;
import com.logvex.crystal.config.ProfileManager;
import com.logvex.crystal.config.ProfileService;
import com.logvex.crystal.cosmetic.CosmeticService;
import com.logvex.crystal.cosmetic.GuiSounds;
import com.logvex.crystal.gui.ClickGui;
import com.logvex.crystal.gui.HudManager;
import com.logvex.crystal.gui.ScreenGuard;
import com.logvex.crystal.logging.ModuleErrorLog;
import com.logvex.crystal.module.ModuleContext;
import com.logvex.crystal.module.ModuleManager;
import com.logvex.crystal.module.ModuleRegistry;
import com.logvex.crystal.notification.NotificationLevel;
import com.logvex.crystal.notification.NotificationManager;
import com.logvex.crystal.setting.Setting;
import com.logvex.crystal.theme.ThemeManager;
import com.logvex.crystal.util.FrameClock;
import java.io.IOException;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint and composition root.
 *
 * <p>Builds the service graph once (theme → notifications → modules → HUD → commands),
 * wires the Fabric callbacks, then ticks and renders everything from those callbacks.
 * Nothing is done at class-load time; all setup happens in {@link #onInitializeClient()}.
 *
 * <p>This is a purely client-side mod: it reads world state and draws an interface. It
 * performs no external process interaction.
 */
public final class CrystalClient implements ClientModInitializer {

    public static final Logger LOG = LoggerFactory.getLogger("LogvexCrystal");

    private static CrystalClient instance;
    private static final ModuleErrorLog ERRORS = new ModuleErrorLog(64);

    /** The shared record of module failures, for the {@code ; errors} diagnostic. */
    public static ModuleErrorLog errors() {
        return ERRORS;
    }

    private ThemeManager theme;
    private NotificationManager notifications;
    private com.logvex.crystal.notification.NotificationStore notificationStore;
    private ModuleManager modules;
    private HudManager hud;
    private CombatService combat;
    private CosmeticService cosmetics;
    private ConfigManager config;
    private AppearanceStore appearanceStore;
    private HudLayoutStore hudLayoutStore;
    private FavoritesStore favoritesStore;
    private ProfileService profileService;
    /** Advanced workspaces: selective apply + preview over the profile store. */
    private com.logvex.crystal.workspace.AdvancedWorkspaceService workspaces;
    private final com.logvex.crystal.context.ContextProfileService contextProfiles =
            new com.logvex.crystal.context.ContextProfileService();
    private final com.logvex.crystal.context.ContextStore contextStore =
            new com.logvex.crystal.context.ContextStore(ConfigPaths.context());
    private CommandManager commands;
    private ClickGui clickGui;
    private KeyBinding clickGuiKey;
    /** Optional keybind that bookmarks the player's current position as a waypoint. */
    private KeyBinding bookmarkKey;
    private final com.logvex.crystal.social.FriendList friends =
            new com.logvex.crystal.social.FriendList(ConfigPaths.friends());
    private final com.logvex.crystal.util.waypoint.WaypointStore waypoints =
            new com.logvex.crystal.util.waypoint.WaypointStore(ConfigPaths.waypoints());
    private final com.logvex.crystal.render.esp.WorldEspCollector worldEsp =
            new com.logvex.crystal.render.esp.WorldEspCollector();
    /** Entity lifecycle: spawn/despawn diffing and bounded per-entity history. */
    private final com.logvex.crystal.entity.EntityTracker entityTracker =
            new com.logvex.crystal.entity.EntityTracker();
    /** Optional Baritone integration: the only place the client talks to Baritone. */
    private final com.logvex.crystal.integration.baritone.BaritoneService baritone =
            new com.logvex.crystal.integration.baritone.BaritoneService();
    /** Shared arbitration for client actions, so two modules cannot act on the same input. */
    private final com.logvex.crystal.coordination.ActionCoordinator actionCoordinator =
            new com.logvex.crystal.coordination.ActionCoordinator();
    /** The live dependency context and the read-only coordination inspector. */
    private com.logvex.crystal.coordination.ModuleCoordinationService coordination;
    /** The shared event stream over observable client state changes. */
    private final com.logvex.crystal.event.ClientEventBus events =
            new com.logvex.crystal.event.ClientEventBus(400L);
    private com.logvex.crystal.event.ClientEventFeed eventFeed;
    /** The deterministic automation rule engine and its persistent store. */
    private final com.logvex.crystal.automation.AutomationEngine automation =
            new com.logvex.crystal.automation.AutomationEngine(events);
    private com.logvex.crystal.automation.AutomationStore automationStore;
    private com.logvex.crystal.automation.ClientAutomationContext automationContext;
    /** The injected service context, kept so the extension API can attach extension modules. */
    private ModuleContext moduleContext;
    /** The stable extension API registry and the extension lifecycle manager. */
    private com.logvex.crystal.api.ApiRegistry apiRegistry;
    private com.logvex.crystal.extension.ExtensionManager extensionManager;
    private com.logvex.crystal.extension.ExtensionPreferences extensionPreferences;

    // ---- Phase 7: optional mini-AI (crash intelligence, local model, reports) ----
    /** The shared mini-AI preferences, persisted to {@code ai.json}. */
    private final com.logvex.crystal.ai.AISettings aiSettings = new com.logvex.crystal.ai.AISettings();
    private com.logvex.crystal.config.AISettingsStore aiSettingsStore;
    /** The crash registry; capture is wired through the uncaught-exception handler. */
    private final com.logvex.crystal.ai.crash.CrashMonitor crashMonitor =
            new com.logvex.crystal.ai.crash.CrashMonitor(50);
    private com.logvex.crystal.ai.crash.CrashStore crashStore;
    /** Deterministic crash analysis plus optional model enhancement. */
    private final com.logvex.crystal.ai.crash.CrashAnalyzerService crashAnalyzer =
            new com.logvex.crystal.ai.crash.CrashAnalyzerService();
    private com.logvex.crystal.ai.MiniAIManager ai;
    private com.logvex.crystal.ai.AiPromptBuilder aiPrompts;
    private com.logvex.crystal.ai.report.BugReportQueue reportQueue;
    private com.logvex.crystal.ai.report.ReportService reportService;
    /** Bounded ring of recent client events attached to a captured crash. */
    private final com.logvex.crystal.ai.context.DiagnosticContext aiContext =
            new com.logvex.crystal.ai.context.DiagnosticContext(64);
    private com.logvex.crystal.ai.crash.CrashCapture aiCapture;
    private volatile com.logvex.crystal.ai.baritone.BaritoneAdvice baritoneAdvice =
            com.logvex.crystal.ai.baritone.BaritoneAdvice.unavailable("not yet evaluated");
    private com.logvex.crystal.event.ClientEventBus.Subscription aiEventSubscription;
    private long lastBaritoneAdviceMillis;

    // ---- Phase 8A: central Command Center (status, compatibility, performance) ----
    /** The unified control centre; null only before bootstrap finishes. */
    private com.logvex.crystal.commandcenter.CommandCenterService commandCenter;
    /** Wall-clock of the last cached status snapshot, so the tick path can throttle it. */
    private long lastCommandCenterSnapshotMillis;

    // ---- Phase 8B: the user-facing Command Center (UI, workspaces, automation) ----
    private com.logvex.crystal.commandcenter.ui.CommandCenterStore commandCenterStore;
    private final com.logvex.crystal.commandcenter.ui.CommandCenterPreferences commandCenterPreferences =
            new com.logvex.crystal.commandcenter.ui.CommandCenterPreferences();
    private com.logvex.crystal.commandcenter.ui.DashboardLayout dashboardLayout =
            com.logvex.crystal.commandcenter.ui.DashboardLayout.defaults();
    private final com.logvex.crystal.commandcenter.ui.PerformanceHistory performanceHistory =
            new com.logvex.crystal.commandcenter.ui.PerformanceHistory();
    private com.logvex.crystal.commandcenter.assistant.AssistantService assistant;
    private com.logvex.crystal.commandcenter.quick.QuickActionEngine quickActions;
    private com.logvex.crystal.commandcenter.ui.DiagnosticNotifier diagnosticNotifier;
    private com.logvex.crystal.commandcenter.ui.UnifiedSearchIndex searchIndex;
    private long lastSearchIndexMillis;

    private boolean wasInWorld;
    private String lastDimension = "";
    private AutoSave autoSave;
    /** Config files that failed to parse during load; reported once the notification manager exists. */
    private final java.util.List<String> loadWarnings = new java.util.ArrayList<>();
    /** True while reading config from disk, so loading does not immediately re-mark it dirty. */
    private boolean loadingConfig;

    /** Opt-in headless driver: {@code -Dlogvex.smoke=true -Dlogvex.smoke.ticks=N}. */
    private static final boolean SMOKE = Boolean.getBoolean("logvex.smoke");
    private static final int SMOKE_TICKS = Integer.getInteger("logvex.smoke.ticks", 440);
    /** Optional directory for dev screenshots of the smoke stages. */
    private static final String SHOT_DIR = System.getProperty("logvex.smoke.shots", "");
    private int smokeTick;
    private int smokeStage;

    /** Saves a screenshot of the current framebuffer when the dev shot directory is set. */
    private void shot(String name) {
        if (SHOT_DIR.isEmpty()) {
            return;
        }
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            LOG.info("[shot] {} screen={} tr={} fb={}x{}", name,
                    c.currentScreen == null ? "null" : c.currentScreen.getClass().getSimpleName(),
                    c.textRenderer != null,
                    c.getFramebuffer().textureWidth, c.getFramebuffer().textureHeight);
            java.io.File dir = new java.io.File(SHOT_DIR);
            dir.mkdirs();
            net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(dir,
                    name + ".png", MinecraftClient.getInstance().getFramebuffer(), 1, text -> {});
        } catch (Throwable t) {
            LOG.warn("smoke screenshot failed: {}", name, t);
        }
    }

    /** The live bootstrap, or null before the client has initialised. */
    public static CrystalClient get() {
        return instance;
    }

    @Override
    public void onInitializeClient() {
        instance = this;
        LOG.info("LOGVEX CRYSTAL initialising");

        // Debounced autosave: any setting change marks the config dirty, and a burst of
        // changes collapses into one write. The shutdown hook flushes a pending save.
        autoSave = new AutoSave(1500L, this::saveAll);
        Setting.onAnyChange(setting -> autoSave.markDirty());
        // Collect config files that failed to parse during load, and warn about them once
        // the notification manager exists (see the end of onInitializeClient).
        ConfigIo.onRecovery(loadWarnings::add);

        theme = new ThemeManager();
        appearanceStore = new AppearanceStore(ConfigPaths.appearance());
        // Load appearance before anything reads a palette. Loading must not mark the config
        // dirty, or every launch would rewrite the files it just read.
        loadingConfig = true;
        try {
            copyInto(appearanceStore.load(), theme.settings());
        } finally {
            loadingConfig = false;
        }
        theme.update();
        // Drop tinted icon variants whenever the palette changes so accents stay correct, and
        // mark the config dirty so an appearance change (preset, accent, slider, toggle) is
        // written by the autosave. Registered after the initial load above, so restoring the
        // saved appearance does not itself trigger a rewrite on every launch.
        theme.addListener(palette -> {
            AssetRegistry.get().releaseTinted();
            requestSave();
        });
        net.fabricmc.fabric.api.resource.v1.ResourceLoader
                .get(net.minecraft.resource.ResourceType.CLIENT_RESOURCES)
                .registerReloader(AssetRegistry.ReloadListener.ID, new AssetRegistry.ReloadListener());
        friends.load();
        waypoints.load();

        notifications = new NotificationManager();
        notificationStore = new com.logvex.crystal.notification.NotificationStore(ConfigPaths.notifications());
        notificationStore.loadInto(notifications.settings());
        notifications.addListener(notification -> {
            if (notifications.settings().sound) {
                GuiSounds.notifyCute();
            }
        });
        modules = new ModuleManager();
        combat = new CombatService();
        cosmetics = new CosmeticService();
        ModuleRegistry.registerAll(modules);
        config = new ConfigManager(modules, ConfigPaths.modules());
        hud = new HudManager(modules, theme);
        hudLayoutStore = new HudLayoutStore(ConfigPaths.hud());
        hudLayoutStore.loadInto(hud.layout());
        moduleContext = new ModuleContext(modules, combat, theme, hud, notifications, config,
                actionCoordinator);
        modules.attachContext(moduleContext);
        // The coordination inspector reads module declarations against the live environment.
        // Services are always present; Baritone is an optional integration; the world-query
        // capability is available whenever a world is loaded.
        coordination = new com.logvex.crystal.coordination.ModuleCoordinationService(modules,
                new com.logvex.crystal.coordination.ClientDependencyContext(modules)
                        .service("combat").service("hud").service("notifications").service("theme")
                        .capability("world-query")
                        .integration("baritone", () -> baritone.available()),
                actionCoordinator);
        loadingConfig = true;
        try {
            config.load();
        } finally {
            loadingConfig = false;
        }
        applyHudVisibility();

        favoritesStore = new FavoritesStore(ConfigPaths.favorites());
        favoritesStore.load();
        profileService = new ProfileService(new ProfileManager(ConfigPaths.profiles()), config,
                hudLayoutStore, appearanceStore, hud.layout(), theme.settings(), this::afterConfigApply);
        workspaces = new com.logvex.crystal.workspace.AdvancedWorkspaceService(profileService);
        contextStore.loadInto(contextProfiles);

        commands = new CommandManager(this);

        // The event feed diffs the state other services already computed; it does no scanning
        // of its own. Created here so it can see the tracker, combat and baritone services.
        eventFeed = new com.logvex.crystal.event.ClientEventFeed(events, modules, entityTracker,
                combat, baritone);

        // The automation engine loads its persisted rules and acts only through the narrow
        // ClientAutomationContext. It is created disabled; the user turns it on explicitly.
        automationStore = new com.logvex.crystal.automation.AutomationStore(ConfigPaths.automation());
        var automationLoad = automationStore.load();
        for (var rule : automationLoad.rules()) {
            automation.addRule(rule);
        }
        if (automationLoad.refusedNewerSchema()) {
            notifications.warn("automation.json uses a newer schema; rules not loaded");
        } else if (automationLoad.skipped() > 0) {
            notifications.warn("Skipped " + automationLoad.skipped() + " malformed automation rule(s)");
        }
        automationContext = new com.logvex.crystal.automation.ClientAutomationContext(modules,
                combat, baritone, profileService, contextProfiles, notifications, errors(),
                actionCoordinator);
        automation.setEnabled(automationLoad.enabled());

        // The stable extension API: a scoped registration registry plus the extension lifecycle.
        // Capabilities are computed live so an extension sees the truth (e.g. Baritone only when
        // it is actually present). Extensions are discovered but never run until approved.
        apiRegistry = new com.logvex.crystal.api.ApiRegistry(
                new com.logvex.crystal.api.ClientApiServices(events, modules, commands, hud,
                        notifications, errors(), moduleContext),
                this::capabilities, modVersion());
        extensionPreferences = new com.logvex.crystal.extension.ExtensionPreferences(
                ConfigPaths.extensions());
        extensionPreferences.load();
        extensionManager = new com.logvex.crystal.extension.ExtensionManager(
                apiRegistry.asExtensionHost(), new com.logvex.crystal.extension.ExtensionManager.Log() {
                    @Override
                    public void warn(String message) {
                        LOG.warn(message);
                    }

                    @Override
                    public void error(String message, Throwable error) {
                        LOG.error(message, error);
                    }
                }, extensionPreferences);
        for (var candidate : com.logvex.crystal.extension.FabricExtensionDiscovery
                .discover(net.fabricmc.loader.api.FabricLoader.getInstance())) {
            extensionManager.discover(candidate);
        }
        int autoEnabled = extensionManager.enableApproved();
        if (autoEnabled > 0) {
            LOG.info("Re-enabled {} approved extension(s)", autoEnabled);
        }

        bootstrapAi();
        bootstrapCommandCenter();
        bootstrapCommandCenterUi();

        registerKeybind();
        registerCosmetics();
        registerCallbacks();

        // Flush any pending save when the client stops normally, so a change made in the
        // last moments before quitting is not lost to the debounce window.
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> shutdown());

        // Cute startup notice, deferred until fonts exist.
        notifications.cute("Logvex Crystal loaded \u2661");
        for (String file : loadWarnings) {
            LOG.warn("Config file {} could not be read; defaults were used", file);
            notifications.warn("Config '" + file + "' was unreadable \u2014 defaults used (backup kept)");
        }
        LOG.info("LOGVEX CRYSTAL ready: {} modules, theme '{}'", modules.size(), theme.palette().preset().label());
    }

    /**
     * Normal shutdown: stop background workers and flush a pending autosave. Runs once on the
     * client thread before the game exits.
     */
    private void shutdown() {
        try {
            autoSave.flush(System.currentTimeMillis());
            LOG.info("LOGVEX CRYSTAL saved configuration on shutdown ({} autosaves)", autoSave.saveCount());
        } catch (Throwable t) {
            LOG.warn("Shutdown save failed", t);
        }
        try {
            modules.shutdownAll();
        } catch (Throwable t) {
            LOG.warn("Module shutdown failed", t);
        }
        try {
            if (extensionManager != null) {
                extensionManager.shutdownAll();
            }
            if (apiRegistry != null) {
                apiRegistry.closeAll();
            }
        } catch (Throwable t) {
            LOG.warn("Extension shutdown failed", t);
        }
        try {
            if (aiEventSubscription != null) {
                aiEventSubscription.close();
            }
            if (reportService != null) {
                reportService.close();
            }
            if (ai != null) {
                ai.close();
            }
        } catch (Throwable t) {
            LOG.warn("AI shutdown failed", t);
        }
    }

    /**
     * Builds the optional mini-AI subsystem: preferences, the crash registry (with an uncaught
     * exception handler), the local model manager, the report queue and the report service.
     *
     * <p>Everything here is inert until the user enables it. Loading {@code ai.json} sets the
     * model choice and feature flags; no model is loaded and no network call is made at startup.
     */
    private void bootstrapAi() {
        aiSettingsStore = new com.logvex.crystal.config.AISettingsStore(ConfigPaths.ai());
        loadingConfig = true;
        try {
            aiSettingsStore.loadInto(aiSettings);
        } finally {
            loadingConfig = false;
        }
        crashStore = new com.logvex.crystal.ai.crash.CrashStore(ConfigPaths.aiCrashes());
        if (aiSettings.keepLocalHistory) {
            for (com.logvex.crystal.ai.crash.CrashRecord record : crashStore.load()) {
                crashMonitor.record(record);
            }
        }
        aiPrompts = new com.logvex.crystal.ai.AiPromptBuilder(new com.logvex.crystal.ai.privacy.PrivacyFilter());
        ai = new com.logvex.crystal.ai.MiniAIManager(ConfigPaths.aiModels(), aiSettings);
        reportQueue = new com.logvex.crystal.ai.report.BugReportQueue(ConfigPaths.aiReports(), 64);
        reportService = new com.logvex.crystal.ai.report.ReportService(aiSettings, reportQueue,
                new com.logvex.crystal.ai.report.BugReportBuilder(new com.logvex.crystal.ai.privacy.PrivacyFilter(),
                        aiSettings), message -> notifications.cute(message));
        aiContext.setSnapshot(new com.logvex.crystal.ai.context.DiagnosticContext.Snapshot(
                modVersion(), MinecraftClient.getInstance().getGameVersion(), loaderVersion(),
                System.getProperty("java.version", "unknown"), System.getProperty("os.name", "unknown"),
                baritoneReportSummary(), aiStatusText()));
        aiCapture = new com.logvex.crystal.ai.crash.CrashCapture(crashMonitor, aiContext,
                () -> aiSettings.crashDetection, this::baritoneReportSummary, this::aiStatusText);
        aiCapture.install();
        crashMonitor.addListener(this::onCrashCaptured);
        // Mirror the client's own event feed into the diagnostic ring, so a report carries the
        // last few things that happened without reading any log file.
        aiEventSubscription = events.subscribe(event -> aiContext.record(event.kind() + ": " + event.detail()));
        LOG.info("Mini-AI ready (enabled={}, localAnalysis={}, email={})",
                aiSettings.enabled, aiSettings.localAnalysis, aiSettings.emailEnabled);
    }

    /** A privacy-safe one-line Baritone summary for a crash record (types only, no coordinates). */
    private String baritoneReportSummary() {
        return com.logvex.crystal.ai.baritone.BaritoneAssistant.reportSummary(
                baritone.status(), baritone.lastActionError());
    }

    /**
     * Builds the central Command Center: the compatibility engine over the live coordination
     * inspector, the performance monitor/intelligence, the status providers that adapt each
     * existing subsystem into a section, and the standard action set.
     *
     * <p>It owns no state of its own beyond the rolling frame window and the cached snapshot;
     * every section is produced from a service that already exists. A provider failure is
     * isolated by the service, so one broken subsystem cannot blank the whole control centre.
     */
    private void bootstrapCommandCenter() {
        var compatibility = new com.logvex.crystal.commandcenter.ModuleCompatibilityEngine(modules, coordination);
        var monitor = new com.logvex.crystal.commandcenter.PerformanceMonitor();
        var intelligence = new com.logvex.crystal.commandcenter.PerformanceIntelligence();
        commandCenter = new com.logvex.crystal.commandcenter.CommandCenterService(
                compatibility, monitor, intelligence);

        commandCenter.addProvider(new com.logvex.crystal.commandcenter.provider.ClientInfoStatusProvider(
                modVersion(), MinecraftClient.getInstance().getGameVersion(), loaderVersion(),
                net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods().size()));
        commandCenter.addProvider(new com.logvex.crystal.commandcenter.provider.ModuleStatusProvider(
                modules, errors()));
        commandCenter.addProvider(new com.logvex.crystal.commandcenter.provider.CoordinationStatusProvider(
                coordination, actionCoordinator));
        commandCenter.addProvider(new com.logvex.crystal.commandcenter.provider.AIStatusProvider(
                aiSettings, ai, crashMonitor, reportService));
        commandCenter.addProvider(new com.logvex.crystal.commandcenter.provider.BaritoneStatusProvider(baritone));
        commandCenter.addProvider(new com.logvex.crystal.commandcenter.provider.EventStatusProvider(events));
        commandCenter.addProvider(new com.logvex.crystal.commandcenter.provider.ProfileStatusProvider(profileService));
        commandCenter.addProvider(new com.logvex.crystal.commandcenter.provider.ExtensionStatusProvider(extensionManager));
        commandCenter.addProvider(new com.logvex.crystal.commandcenter.provider.AutomationStatusProvider(automation));
        commandCenter.addProvider(new com.logvex.crystal.commandcenter.provider.ThemeStatusProvider(theme));

        var host = new com.logvex.crystal.commandcenter.ClientCommandCenterHost(this);
        var standardActions = new com.logvex.crystal.commandcenter.StandardActions(host, commandCenter.actions());
        standardActions.registerAll();
        // Give the Command Center a toggle verb for every module, so the GUI and the action
        // registry can drive modules by name without holding the module object.
        java.util.List<String> moduleNames = new java.util.ArrayList<>();
        for (var module : modules.all()) {
            moduleNames.add(module.name());
        }
        standardActions.registerModuleToggles(moduleNames);
        LOG.info("Command Center ready: {} providers, {} actions", commandCenter.providers().size(),
                commandCenter.actions().size());
    }

    /**
     * The Phase 8B user-facing layer: it loads the Command Center's own preferences, the
     * dashboard layout and the quick-action definitions, then builds the assistant, the
     * diagnostic notifier and the quick-action engine over the services 8A already created.
     *
     * <p>Everything here is optional and degrades to defaults: a missing or malformed store
     * leaves the 8A backend untouched, so the Command Center still works.
     */
    private void bootstrapCommandCenterUi() {
        commandCenterStore = new com.logvex.crystal.commandcenter.ui.CommandCenterStore(
                ConfigPaths.commandCenter());
        var loaded = commandCenterStore.load();
        copyInto(loaded.preferences(), commandCenterPreferences);
        dashboardLayout = loaded.layout();
        for (var action : loaded.quickActions()) {
            quickActions().upsert(action);
        }

        assistant = new com.logvex.crystal.commandcenter.assistant.AssistantService(
                () -> commandCenter == null ? null : commandCenter.statusModel(),
                () -> commandCenter == null ? null : commandCenter.performance(),
                () -> ai, () -> aiSettings.localAnalysis);
        diagnosticNotifier = new com.logvex.crystal.commandcenter.ui.DiagnosticNotifier(
                notifications, commandCenterPreferences);
        quickActions.setEnabled(quickActions.size() > 0);
        LOG.info("Command Center UI ready: {} quick action(s), default workspace {}",
                quickActions.size(), commandCenterPreferences.defaultWorkspace.label());
    }

    /** The quick-action engine, built lazily so bootstrap order does not matter. */
    public com.logvex.crystal.commandcenter.quick.QuickActionEngine quickActions() {
        if (quickActions == null) {
            quickActions = new com.logvex.crystal.commandcenter.quick.QuickActionEngine(
                    new com.logvex.crystal.commandcenter.quick.ClientQuickActionHandler(this));
        }
        return quickActions;
    }

    /** The quick-action context, rebuilt on demand from the live services. */
    private com.logvex.crystal.commandcenter.quick.QuickActionContext quickActionContext() {
        return new com.logvex.crystal.commandcenter.quick.ClientQuickActionContext(
                () -> commandCenter == null ? null : commandCenter.statusModel(),
                () -> commandCenter == null ? null : commandCenter.performance(),
                () -> errors().totalRecorded(),
                () -> crashMonitor.size(),
                () -> baritone.available(),
                () -> ai != null && ai.isLoaded(),
                () -> MinecraftClient.getInstance() != null && MinecraftClient.getInstance().world != null);
    }

    /** Copies the loaded preferences into the live holder, so callers keep one stable instance. */
    private static void copyInto(com.logvex.crystal.commandcenter.ui.CommandCenterPreferences source,
                                 com.logvex.crystal.commandcenter.ui.CommandCenterPreferences target) {
        var copy = source.copy();
        target.defaultWorkspace = copy.defaultWorkspace;
        target.refreshIntervalMillis = copy.refreshIntervalMillis;
        target.historyIntervalMillis = copy.historyIntervalMillis;
        target.hudShowFindings = copy.hudShowFindings;
        target.notificationsEnabled = copy.notificationsEnabled;
        target.dedupeNotifications = copy.dedupeNotifications;
        target.notificationCooldownMillis = copy.notificationCooldownMillis;
        target.notificationRateLimit = copy.notificationRateLimit;
        target.autoApplyQuickActions = copy.autoApplyQuickActions;
        target.reducedMotion = copy.reducedMotion;
    }

    /** Persists the Command Center's own preferences, layout and quick actions. */
    public void saveCommandCenter() {
        if (commandCenterStore == null) {
            return;
        }
        try {
            commandCenterStore.save(commandCenterPreferences, dashboardLayout, quickActions().actions());
        } catch (IOException e) {
            LOG.warn("Failed to save the Command Center configuration", e);
        }
    }

    /** Re-reads the Command Center's own preferences, layout and quick actions. */
    public void reloadCommandCenter() {
        if (commandCenterStore == null) {
            return;
        }
        var loaded = commandCenterStore.load();
        copyInto(loaded.preferences(), commandCenterPreferences);
        dashboardLayout = loaded.layout();
        quickActions().clear();
        for (var action : loaded.quickActions()) {
            quickActions().upsert(action);
        }
    }

    /**
     * The mini-AI per-tick work: advance the model manager's idle-unload clock and refresh the
     * cached Baritone advice at a low rate. Both are cheap; the model's heavy work happens on its
     * own worker thread, so the game thread is never blocked.
     */
    private void tickAi(MinecraftClient client) {
        if (ai == null) {
            return;
        }
        long now = System.currentTimeMillis();
        ai.tick(now);
        if (now - lastBaritoneAdviceMillis >= 1000) {
            lastBaritoneAdviceMillis = now;
            baritoneAdvice = com.logvex.crystal.ai.baritone.BaritoneAssistant.advise(
                    baritone.status(), baritone.history(), baritone.lastActionError(), baritone.paused());
        }
    }

    /** Called on the monitor's thread when a genuinely new crash is recorded. */
    private void onCrashCaptured(com.logvex.crystal.ai.crash.CrashRecord record) {
        if (!aiSettings.crashDetection) {
            return;
        }
        try {
            crashStore.save(crashMonitor.history());
        } catch (IOException e) {
            LOG.warn("Could not persist the crash history", e);
        }
        com.logvex.crystal.ai.crash.Diagnosis diagnosis = crashAnalyzer.diagnose(record);
        if (aiSettings.reportQueue) {
            reportService.onCrash(record, diagnosis);
        }
        if (aiSettings.localAnalysis) {
            ai.submitOnce("crash:" + record.fingerprint(), aiPrompts.crashPrompt(record, diagnosis),
                    text -> crashAnalyzer.enhance(record.fingerprint(), text));
        }
    }

    /** A short, privacy-safe description of the AI subsystem for a crash record. */
    private String aiStatusText() {
        if (ai == null) {
            return "not initialised";
        }
        return ai.state().label() + ", queue " + ai.queueDepth() + "/" + ai.queueLimit();
    }

    /**
     * The Command Center's per-tick step. It samples the performance counters every tick
     * (cheap: one array write and a few reads) and rebuilds the cached status snapshot at
     * most twice a second, so opening the HUD or a screen never triggers a fresh scan.
     */
    private void tickCommandCenter() {
        if (commandCenter == null) {
            return;
        }
        long now = System.currentTimeMillis();
        int activeModules = 0;
        double worstTick = 0;
        double totalTick = 0;
        String worstName = "";
        for (var module : modules.all()) {
            if (!module.isEnabled()) {
                continue;
            }
            activeModules++;
            double nanos = module.lastTickNanos();
            totalTick += nanos / 1_000_000.0;
            if (nanos > worstTick) {
                worstTick = nanos / 1_000_000.0;
                worstName = module.name();
            }
        }
        int aiQueue = ai == null ? 0 : ai.queueDepth();
        int aiRejected = ai == null ? 0 : ai.rejectedCount();
        int backlog = events.history().size();
        int activeActions = actionCoordinator.activeCount();
        var sample = commandCenter.samplePerformance(now, activeModules, worstName, worstTick, totalTick,
                aiQueue, aiRejected, backlog, activeActions).sample();
        if (now - lastCommandCenterSnapshotMillis >= 500) {
            lastCommandCenterSnapshotMillis = now;
            var model = commandCenter.snapshot();
            // The diagnostic notifier and the quick-action engine only see a fresh snapshot,
            // so they run at the same throttled rate rather than every frame.
            if (diagnosticNotifier != null) {
                diagnosticNotifier.observe(model, now);
            }
        }
        // History sampling is internally throttled to the user's interval.
        performanceHistory.sampleIfDue(now, sample, usedMemoryBytes());
        if (quickActions != null) {
            quickActions.tick(quickActionContext(), now);
        }
    }

    /** Currently used JVM heap, for the memory history series. */
    private static long usedMemoryBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    /** Samples performance and the history right now (used by the Performance workspace). */
    public void samplePerformanceNow() {
        tickCommandCenter();
        long now = System.currentTimeMillis();
        if (commandCenter != null) {
            performanceHistory.record(commandCenter.performance().sample(), usedMemoryBytes(), now);
        }
    }

    private static String loaderVersion() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("fabricloader")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    private void registerKeybind() {
        clickGuiKey = new KeyBinding("key.logvex-crystal.clickgui", InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT, KeyBinding.Category.MISC);
        net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(clickGuiKey);
        bookmarkKey = new KeyBinding("key.logvex-crystal.bookmark", InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN, KeyBinding.Category.MISC);
        net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(bookmarkKey);
    }

    /**
     * Registers the optional cosmetic player accessories: the model layer is baked with the
     * vanilla entity models, and a feature renderer is attached to the player renderer. Both
     * are inert until the PlayerCosmetics module is enabled, so this costs nothing when the
     * user leaves the cosmetic off.
     */
    private void registerCosmetics() {
        net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(
                com.logvex.crystal.cosmetic.render.CosmeticModelLayers.PLAYER_ACCESSORIES,
                com.logvex.crystal.cosmetic.render.CatCosmeticsModel::getTexturedModelData);
        registerPlayerFeatureRenderer();
    }

    @SuppressWarnings("unchecked")
    private void registerPlayerFeatureRenderer() {
        net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
                (entityType, renderer, helper, context) -> {
                    if (entityType != net.minecraft.entity.EntityType.PLAYER) {
                        return;
                    }
                    if (!(renderer instanceof net.minecraft.client.render.entity.PlayerEntityRenderer playerRenderer)) {
                        return;
                    }
                    com.logvex.crystal.cosmetic.render.CatCosmeticsModel model =
                            new com.logvex.crystal.cosmetic.render.CatCosmeticsModel(
                                    com.logvex.crystal.cosmetic.render.CatCosmeticsModel.bakedPart(context));
                    helper.register((net.minecraft.client.render.entity.feature.FeatureRenderer)
                            new com.logvex.crystal.cosmetic.render.PlayerCosmeticFeatureRenderer(
                                    playerRenderer, model));
                });
    }

    private void registerCallbacks() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
        // Intercept the local command prefix before the line is sent to the server. Anarchy
        // servers reject unknown slash commands, so Crystal commands never leave the client.
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> !commands.handleChat(message));
        ClientSendMessageEvents.ALLOW_COMMAND.register(message -> !commands.handleChat("." + message));
        // Register the whole Crystal HUD as one layer attached after the vanilla HUD.
        com.logvex.crystal.compat.Compat.registerHud((context, tickCounter) -> renderHud(
                (net.minecraft.client.gui.DrawContext) context,
                (net.minecraft.client.render.RenderTickCounter) tickCounter));
        // World-space ESP is collected and drawn inside the world render pass, where the
        // camera-relative matrix stack and vertex consumers are valid.
        com.logvex.crystal.compat.Compat.registerWorld(this::renderWorldEsp);
    }

    /** The Crystal HUD pass: every enabled HUD element, then the cosmetic particles. */
    private void renderHud(net.minecraft.client.gui.DrawContext context,
                           net.minecraft.client.render.RenderTickCounter tickCounter) {
        if (!ScreenGuard.fontsReady()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        FrameClock.tick(client);
        // While a screen is open the HUD is drawn beneath it as a live preview; the
        // HudEditor screen adds its own drag frames on top.
        if (client.currentScreen == null || client.currentScreen instanceof com.logvex.crystal.gui.ClickGui
                || client.currentScreen instanceof com.logvex.crystal.gui.HudEditorScreen) {
            hud.render(context, client, tickCounter.getDynamicDeltaTicks());
        }
        if (commandCenter != null) {
            commandCenter.recordFrame(FrameClock.deltaMillis());
        }
        cosmetics.renderParticles(context, client);
    }

    /**
     * Collects every ESP module's primitives into one reused buffer and draws them in a
     * single pass. Bails out early when nothing is registered or no world is loaded, so the
     * hook costs nothing when ESP is off.
     */
    private void renderWorldEsp(Object context) {
        if (!hud.hasWorldEsp()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return;
        }
        try {
            worldEsp.clear();
            hud.collectWorldEsp(worldEsp, client);
            com.logvex.crystal.render.esp.WorldEspRenderer.render(context, client,
                    com.logvex.crystal.compat.Compat.worldCamera(context), worldEsp);
        } catch (Throwable t) {
            LOG.error("World ESP render failed", t);
        }
    }

    private void onEndTick(MinecraftClient client) {
        if (!ScreenGuard.fontsReady()) {
            return;
        }
        try {
            combat.tick(client);
            com.logvex.crystal.world.WorldQuery.get().tick(client);
            com.logvex.crystal.entity.EntityQuery.get().tick(client);
            entityTracker.update(com.logvex.crystal.entity.EntityQuery.get().all(), System.currentTimeMillis());
            com.logvex.crystal.world.SessionStats.get().tick(client);
            baritone.tick(client);
            modules.tickAll();
            actionCoordinator.tick(System.currentTimeMillis());
            eventFeed.tick(client);
            tickAi(client);
            tickCommandCenter();
            automation.tick(automationContext, System.currentTimeMillis());
            notifications.prune();
            cosmetics.tick(client);
            while (clickGuiKey.wasPressed()) {
                if (!(client.currentScreen instanceof ClickGui)) {
                    clickGui().open();
                }
            }
            while (bookmarkKey.wasPressed()) {
                bookmarkPosition(client);
            }
            handleWorldTransitions(client);
            evaluateContextProfiles(client);
            autoSave.tick(System.currentTimeMillis());
            if (SMOKE) {
                runSmokeStep(client);
            }
        } catch (Throwable t) {
            LOG.error("Uncaught error in client tick", t);
        }
    }

    /**
     * The headless GUI smoke sequence. Exercises the screens and theme switching that the
     * normal tick path cannot reach without a human, and fails loudly if a screen fails to
     * install or a palette fails to rebuild. Enabled only by the smoke system property.
     */
    private void runSmokeStep(MinecraftClient client) {
        if (!ScreenGuard.fontsReady()) {
            return;
        }
        smokeTick++;
        switch (smokeStage) {
            case 0 -> {
                if (smokeTick < 40) {
                    return;
                }
                clickGui().open();
                require(client.currentScreen instanceof ClickGui, "ClickGUI did not install");
                require(((ClickGui) client.currentScreen).isRenderable(),
                        "ClickGUI was installed with a null text renderer");
                smokeStage++;
            }
            case 1 -> {
                if (smokeTick < 70) {
                    return;
                }
                // The ClickGUI has now drawn at least once, so the asset pack must have been
                // resolved from the mod's own resources: prove the textures loaded, not fell
                // back to the procedural motifs.
                var assets = com.logvex.crystal.assets.AssetRegistry.get();
                require(assets.image("logo") != null, "logo.png did not load from the asset pack");
                require(assets.icon("crystal", 0xFFFFFFFF) != null, "crystal icon did not load");
                require(assets.registered() > 0, "no textures were registered");
                require(assets.failures() == 0, "asset pack reported " + assets.failures() + " failures");
                LOG.info("LOGVEX CRYSTAL asset pack: {} assets, {} textures registered",
                        com.logvex.crystal.assets.AssetManifest.size(), assets.registered());
                shot("01-clickgui-combat");
                clickGui().showAppearance();
                require(((ClickGui) client.currentScreen).isAppearance(), "Appearance tab not selected");
                smokeStage++;
            }
            case 2 -> {
                if (smokeTick < 100) {
                    return;
                }
                shot("02-clickgui-appearance");
                for (com.logvex.crystal.theme.ThemePreset preset : com.logvex.crystal.theme.ThemePreset.values()) {
                    theme.applyPreset(preset);
                    require(theme.palette().preset() == preset, "Palette did not switch to " + preset);
                }
                smokeStage++;
            }
            case 3 -> {
                if (smokeTick < 130) {
                    return;
                }
                client.setScreen(null);
                // With no world loaded the client installs a TitleScreen, so only assert
                // that the ClickGUI is no longer the active screen.
                require(!(client.currentScreen instanceof ClickGui), "ClickGUI did not close");
                smokeStage++;
            }
            case 4 -> {
                if (smokeTick < 160) {
                    return;
                }
                client.setScreen(new com.logvex.crystal.gui.HudEditorScreen(hud, theme));
                require(client.currentScreen instanceof com.logvex.crystal.gui.HudEditorScreen,
                        "HUD editor did not install");
                smokeStage++;
            }
            case 5 -> {
                if (smokeTick < 190) {
                    return;
                }
                client.setScreen(null);
                saveAll();
                require(java.nio.file.Files.exists(ConfigPaths.appearance()),
                        "appearance.json was not written");
                smokeStage++;
            }
            case 6 -> {
                if (smokeTick < 220) {
                    return;
                }
                // Prove the HUD layout survives a save/reload round trip.
                var entry = hud.layout().entry("watermark");
                entry.customPosition = true;
                entry.x = 33;
                entry.opacity = 0.4f;
                entry.scale = 1.5f;
                saveAll();
                require(java.nio.file.Files.exists(ConfigPaths.hud()), "hud.json was not written");
                // Wipe the in-memory values, then reload from disk.
                hud.layout().clear();
                reloadAll();
                var reloaded = hud.layout().entry("watermark");
                require(reloaded.customPosition && reloaded.x == 33,
                        "HUD layout position did not persist");
                require(Math.abs(reloaded.opacity - 0.4f) < 1e-4 && Math.abs(reloaded.scale - 1.5f) < 1e-4,
                        "HUD layout opacity/scale did not persist");
                smokeStage++;
            }
            case 7 -> {
                if (smokeTick < 250) {
                    return;
                }
                // Toggle the ESP modules to prove their enable/disable path (and world-ESP
                // provider registration) runs without a world loaded.
                for (String esp : new String[]{"EntityEsp", "CrystalEsp", "StorageEsp", "HoleEsp"}) {
                    var module = modules.byName(esp);
                    require(module != null, "ESP module not registered: " + esp);
                    module.toggle(modules);
                    require(module.isEnabled(), esp + " did not enable");
                    module.toggle(modules);
                    require(!module.isEnabled(), esp + " did not disable");
                }
                require(!hud.hasWorldEsp(), "world ESP providers were not released on disable");
                smokeStage++;
            }
            case 8 -> {
                if (smokeTick < 280) {
                    return;
                }
                // The friend list must round-trip through disk as well.
                friends.add("SmokeFriend");
                saveAll();
                require(java.nio.file.Files.exists(ConfigPaths.friends()), "friends.json was not written");
                friends.remove("SmokeFriend");
                saveAll();
                friends.load();
                require(!friends.isFriend("SmokeFriend"), "friend removal did not persist");
                // Waypoints must round-trip too.
                waypoints.put(new com.logvex.crystal.util.waypoint.Waypoint("SmokeSpot", 1, 2, 3,
                        0xFFFF00FF, ""));
                saveAll();
                require(java.nio.file.Files.exists(ConfigPaths.waypoints()), "waypoints.json was not written");
                waypoints.clear();
                waypoints.load();
                require(waypoints.byName("SmokeSpot") != null, "waypoint did not persist");
                waypoints.clear();
                saveAll();
                smokeStage++;
            }
            case 9 -> {
                if (smokeTick < 300) {
                    return;
                }
                // ChestLogger: prove the module enables, clusters a synthetic haul through
                // its own worker, and delivers exactly one local notification - with no
                // world loaded, which also proves the disabled/idle path is safe.
                var chestLogger = modules.byName("ChestLogger");
                require(chestLogger != null, "ChestLogger module not registered");
                chestLogger.toggle(modules);
                require(chestLogger.isEnabled(), "ChestLogger did not enable");
                var scan = (com.logvex.crystal.module.misc.ChestLoggerModule) chestLogger;
                java.util.List<com.logvex.crystal.scan.ChestPoint> haul = new java.util.ArrayList<>();
                for (int i = 0; i < 30; i++) {
                    haul.add(new com.logvex.crystal.scan.ChestPoint(i, 64, 0));
                }
                scan.service().submit("minecraft:overworld", haul, java.util.List.of(), 1_000L);
                var reports = awaitReports(scan.service(), 1);
                require(reports.size() == 1, "ChestLogger did not produce exactly one report");
                require(reports.get(0).chestCount() == 30, "ChestLogger counted the wrong number of chests");
                // A second identical submission must be suppressed as a duplicate.
                scan.service().submit("minecraft:overworld", haul, java.util.List.of(), 2_000L);
                sleepQuietly(200L);
                require(scan.service().poll().isEmpty(), "ChestLogger duplicate was not suppressed");
                chestLogger.toggle(modules);
                require(!chestLogger.isEnabled(), "ChestLogger did not disable");
                smokeStage++;
            }
            case 10 -> {
                if (smokeTick < 320) {
                    return;
                }
                // Favourites round-trip through disk. Normalise first: a previous smoke run
                // leaves AutoCrystal favourited on disk, so a bare toggle would flip it off.
                if (favoritesStore.isFavorite("AutoCrystal")) {
                    favoritesStore.toggleFavorite("AutoCrystal");
                }
                require(!favoritesStore.isFavorite("AutoCrystal"), "favourite state was not normalised");
                favoritesStore.toggleFavorite("AutoCrystal");
                require(favoritesStore.isFavorite("AutoCrystal"), "favourite was not recorded");
                favoritesStore.markUsed("AutoTotem");
                favoritesStore.markUsed("AutoCrystal");
                require(favoritesStore.recent().get(0).equals("AutoCrystal"), "recents ordering wrong");
                saveAll();
                require(java.nio.file.Files.exists(ConfigPaths.favorites()), "favorites.json was not written");
                smokeStage++;
            }
            case 11 -> {
                if (smokeTick < 350) {
                    return;
                }
                // Profile save -> mutate -> load round-trip through the real service.
                require(profileService != null, "profile service was not created");
                try {
                    boolean saved = profileService.save("smoke-profile", true);
                    require(saved, "profile was not written");
                } catch (java.io.IOException e) {
                    throw new IllegalStateException("SMOKE FAILURE: profile save threw", e);
                }
                require(java.nio.file.Files.exists(ConfigPaths.profiles().resolve("smoke-profile.json")),
                        "profile file missing");
                // Change something, then load the profile back and prove it was restored.
                var entityEsp = modules.byName("EntityEsp");
                require(entityEsp != null, "EntityEsp missing");
                if (!entityEsp.isEnabled()) {
                    entityEsp.toggle(modules);
                }
                boolean ok = profileService.apply("smoke-profile");
                require(ok, "profile did not load");
                require(favoritesStore.isFavorite("AutoCrystal"), "favourite lost after profile load");
                // Clean up the smoke profile so repeat runs start fresh.
                profileService.profiles().delete("smoke-profile");
                smokeStage++;
            }
            case 12 -> {
                if (smokeTick < 380) {
                    return;
                }
                // Exercise every new screen installing without a null renderer.
                client.setScreen(new com.logvex.crystal.gui.KeybindScreen(modules, theme, null));
                require(client.currentScreen instanceof com.logvex.crystal.gui.KeybindScreen,
                        "KeybindScreen did not install");
                smokeStage++;
            }
            case 13 -> {
                if (smokeTick < 400) {
                    return;
                }
                client.setScreen(new com.logvex.crystal.gui.ProfileScreen(profileService, theme));
                require(client.currentScreen instanceof com.logvex.crystal.gui.ProfileScreen,
                        "ProfileScreen did not install");
                client.setScreen(null);
                smokeStage++;
            }
            case 14 -> {
                if (smokeTick < 420) {
                    return;
                }
                // Contextual-profile rules must round-trip through disk, and the notification
                // corner/scale preferences must persist and be honoured by the layout helper.
                contextProfiles.setEnabled(true);
                contextProfiles.setCooldownMillis(4321);
                contextProfiles.clear();
                contextProfiles.add(com.logvex.crystal.context.ContextProfileBinding.of("smoke-ctx",
                        new com.logvex.crystal.context.ContextRule("minecraft:the_nether", "smoke.net", 0.4)));
                notifications.settings().position =
                        com.logvex.crystal.notification.NotificationSettings.Position.BOTTOM_LEFT;
                notifications.settings().scale = 1.25f;
                saveAll();
                require(java.nio.file.Files.exists(ConfigPaths.context()), "context.json was not written");
                require(com.logvex.crystal.notification.NotificationLayout.leftAligned(
                                notifications.settings().position), "bottom-left must be left-aligned");
                contextProfiles.clear();
                contextProfiles.setEnabled(false);
                reloadAll();
                require(contextProfiles.enabled() && contextProfiles.cooldownMillis() == 4321,
                        "context settings did not persist");
                require(contextProfiles.bindings().size() == 1
                                && contextProfiles.bindings().get(0).profileName().equals("smoke-ctx"),
                        "context rule did not persist");
                require(notifications.settings().position
                                == com.logvex.crystal.notification.NotificationSettings.Position.BOTTOM_LEFT,
                        "notification position did not persist");
                require(Math.abs(notifications.settings().scale - 1.25f) < 1e-4,
                        "notification scale did not persist");
                // Leave the client in a clean state for the next run.
                contextProfiles.clear();
                contextProfiles.setEnabled(false);
                notifications.settings().position =
                        com.logvex.crystal.notification.NotificationSettings.Position.TOP_RIGHT;
                notifications.settings().scale = 1.0f;
                saveAll();
                smokeStage++;
            }
            default -> {
                if (smokeTick >= SMOKE_TICKS) {
                    LOG.info("LOGVEX CRYSTAL smoke OK after {} ticks ({} modules)", smokeTick, modules.size());
                    client.scheduleStop();
                }
            }
        }
    }

    /** Polls the logger until it has produced {@code expected} reports, or a short timeout. */
    private static java.util.List<com.logvex.crystal.scan.ChestReport> awaitReports(
            com.logvex.crystal.scan.ChestLoggerService service, int expected) {
        java.util.List<com.logvex.crystal.scan.ChestReport> collected = new java.util.ArrayList<>();
        for (int attempt = 0; attempt < 50; attempt++) {
            collected.addAll(service.poll());
            if (collected.size() >= expected) {
                return collected;
            }
            sleepQuietly(10L);
        }
        return collected;
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("SMOKE FAILURE: " + message);
        }
    }

    /**
     * Bookmarks the player's current position as an auto-named waypoint, from the configurable
     * keybind. The name is derived from the position so repeated presses never collide.
     */
    private void bookmarkPosition(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }
        int x = (int) Math.floor(client.player.getX());
        int y = (int) Math.floor(client.player.getY());
        int z = (int) Math.floor(client.player.getZ());
        String dimension = client.world.getRegistryKey().getValue().toString();
        String name = "bookmark-" + x + "_" + z;
        var candidate = new com.logvex.crystal.util.waypoint.Waypoint(name, x, y, z,
                theme.palette().primary(), dimension);
        var duplicate = waypoints.duplicateOf(candidate, 2);
        waypoints.put(candidate);
        requestSave();
        notify("Bookmarked '" + name + "' \u2661", NotificationLevel.SUCCESS);
        if (duplicate != null) {
            notify("Nearby: '" + duplicate.name() + "'", NotificationLevel.INFO);
        }
    }

    private void handleWorldTransitions(MinecraftClient client) {
        boolean inWorld = client.player != null && client.world != null;
        if (wasInWorld && !inWorld) {
            modules.onDisconnect();
            com.logvex.crystal.world.WorldQuery.get().invalidate();
            com.logvex.crystal.entity.EntityQuery.get().invalidate();
            entityTracker.clear();
            notifications.clear();
            // A disconnect is a natural save point: enabled states change on disconnect.
            autoSave.markDirty();
        } else if (inWorld) {
            // A dimension change keeps the player connected but replaces the world, so the
            // tracked entities are stale. Detect it by the dimension id changing.
            String dimension = client.world.getRegistryKey().getValue().toString();
            if (!dimension.equals(lastDimension)) {
                entityTracker.clear();
                com.logvex.crystal.entity.EntityQuery.get().invalidate();
            }
            lastDimension = dimension;
        }
        wasInWorld = inWorld;
    }

    /**
     * Applies opt-in contextual profile rules. The service decides; this method only acts,
     * and never applies a profile while a combat target is present so a switch cannot happen
     * mid-fight. Called every tick, but the service's own cooldown keeps it cheap.
     */
    private void evaluateContextProfiles(MinecraftClient client) {
        if (!contextProfiles.enabled() || client.player == null || client.world == null) {
            return;
        }
        boolean inCombat = combat.currentTarget() != null;
        String dimension = client.world.getRegistryKey().getValue().toString();
        String server = client.getCurrentServerEntry() == null ? "" : client.getCurrentServerEntry().address;
        float maxHealth = client.player.getMaxHealth();
        double healthFraction = maxHealth <= 0 ? 1.0 : client.player.getHealth() / maxHealth;
        var snapshot = com.logvex.crystal.context.ContextSnapshot.of(dimension, server, healthFraction,
                client.player.isAlive());
        long now = System.currentTimeMillis();
        contextProfiles.decide(snapshot, inCombat, now).ifPresent(profile -> {
            if (profileService.apply(profile)) {
                contextProfiles.recordSwitch(profile, now);
                notifications.info("Context: applied profile '" + profile + "'");
            }
        });
    }

    /** The contextual-profile rule engine. */
    public com.logvex.crystal.context.ContextProfileService contextProfiles() {
        return contextProfiles;
    }

    /** The contextual-profile persistence. */
    public com.logvex.crystal.context.ContextStore contextStore() {
        return contextStore;
    }

    /**
     * Marks the configuration dirty so the debounced autosave will write it. Public so the
     * GUI, commands and the HUD editor can request a save without writing on every frame.
     */
    public void requestSave() {
        if (autoSave != null && !loadingConfig) {
            autoSave.markDirty();
        }
    }

    /** Forces an immediate save (used by explicit save actions and on shutdown). */
    public void saveNow() {
        saveAll();
        if (autoSave != null) {
            autoSave.flush(System.currentTimeMillis());
        }
    }

    /** Persists appearance, HUD layout and module state. Safe to call from the client thread. */
    public void saveAll() {
        try {
            appearanceStore.save(theme.settings());
            hudLayoutStore.save(hud.layout());
            config.save();
            friends.save();
            waypoints.save();
            favoritesStore.save();
            notificationStore.save(notifications.settings());
            contextStore.save(contextProfiles);
            if (aiSettingsStore != null) {
                aiSettingsStore.save(aiSettings);
            }
            saveCommandCenter();
        } catch (IOException e) {
            LOG.warn("Failed to save configuration", e);
        }
    }

    /** Reloads appearance + module state from disk and re-applies it live. */
    public void reloadAll() {
        loadingConfig = true;
        try {
            copyInto(appearanceStore.load(), theme.settings());
            theme.update();
            hudLayoutStore.loadInto(hud.layout());
            config.load();
            friends.load();
            waypoints.load();
            notificationStore.loadInto(notifications.settings());
            contextStore.loadInto(contextProfiles);
            if (aiSettingsStore != null) {
                aiSettingsStore.loadInto(aiSettings);
            }
            reloadCommandCenter();
        } finally {
            loadingConfig = false;
        }
        applyHudVisibility();
        if (ai != null) {
            ai.reconfigure();
        }
        var aiModule = modules.byClass(com.logvex.crystal.module.misc.AIAssistantModule.class);
        if (aiModule != null) {
            aiModule.syncFromSettings();
        }
    }

    /**
     * Syncs HUD element visibility with the modules that own each element, so the module
     * list and the HUD cannot disagree.
     *
     * <p>Two kinds of element: a {@code HudToggleModule} owns its element and dictates
     * visibility, while a HUD element with no owning module (the notifications, for example)
     * keeps whatever the user set in the layout editor - so this pass must never force such
     * an element visible. Called on load and after a profile is applied; the per-element
     * drag state is not disturbed.
     */
    private void applyHudVisibility() {
        for (com.logvex.crystal.module.Module module : modules.all()) {
            if (module instanceof com.logvex.crystal.module.hud.HudToggleModule toggle) {
                hud.setElementVisible(toggle.elementId(), module.isEnabled());
            }
        }
        // The AI HUD element is owned by the AIAssistant module, which is not a HudToggleModule.
        var aiModule = modules.byClass(com.logvex.crystal.module.misc.AIAssistantModule.class);
        if (aiModule != null) {
            hud.setElementVisible(com.logvex.crystal.gui.hud.AIHudElement.ID, aiModule.isEnabled());
        }
    }

    /**
     * Runs after a profile is applied: rebuilds the palette from the restored appearance,
     * re-syncs the HUD element visibility and pushes the theme state into the Theme module
     * so its settings do not disagree with the profile.
     */
    private void afterConfigApply() {
        theme.update();
        applyHudVisibility();
        var themeModule = modules.byClass(com.logvex.crystal.module.misc.ThemeModule.class);
        if (themeModule != null) {
            themeModule.syncFromTheme();
        }
    }

    private static void copyInto(com.logvex.crystal.theme.ThemeSettings source,
                                 com.logvex.crystal.theme.ThemeSettings target) {
        ProfileService.copyInto(source, target);
    }

    // ------------------------------------------------------------ accessors

    public ThemeManager theme() {
        return theme;
    }

    public NotificationManager notifications() {
        return notifications;
    }

    public ModuleManager modules() {
        return modules;
    }

    public HudManager hud() {
        return hud;
    }

    public CombatService combat() {
        return combat;
    }

    public CosmeticService cosmetics() {
        return cosmetics;
    }

    public ConfigManager config() {
        return config;
    }

    public CommandManager commands() {
        return commands;
    }

    /** The central Command Center: unified status, module compatibility and performance. */
    public com.logvex.crystal.commandcenter.CommandCenterService commandCenter() {
        return commandCenter;
    }

    // ------------------------------------------------------------ Command Center UI

    /** The Command Center's own preferences (default workspace, intervals, notification policy). */
    public com.logvex.crystal.commandcenter.ui.CommandCenterPreferences commandCenterPreferences() {
        return commandCenterPreferences;
    }

    /** The user-arranged dashboard layout. */
    public com.logvex.crystal.commandcenter.ui.DashboardLayout dashboardLayout() {
        return dashboardLayout;
    }

    /** The bounded performance history behind the Performance workspace charts. */
    public com.logvex.crystal.commandcenter.ui.PerformanceHistory performanceHistory() {
        return performanceHistory;
    }

    /** The AI assistant workspace service (deterministic, with optional model enhancement). */
    public com.logvex.crystal.commandcenter.assistant.AssistantService assistant() {
        return assistant;
    }

    /** The diagnostic notifier that turns new problems into (deduplicated) notifications. */
    public com.logvex.crystal.commandcenter.ui.DiagnosticNotifier diagnosticNotifier() {
        return diagnosticNotifier;
    }

    /**
     * The unified search index, rebuilt at most once a second so the screen can search modules,
     * settings, commands, workspaces, profiles and actions from one box.
     */
    public com.logvex.crystal.commandcenter.ui.UnifiedSearchIndex searchIndex() {
        long now = System.currentTimeMillis();
        if (searchIndex == null || now - lastSearchIndexMillis > 1000) {
            searchIndex = buildSearchIndex();
            lastSearchIndexMillis = now;
        }
        return searchIndex;
    }

    /** Rebuilds the unified search index from the live services. */
    private com.logvex.crystal.commandcenter.ui.UnifiedSearchIndex buildSearchIndex() {
        var index = new com.logvex.crystal.commandcenter.ui.UnifiedSearchIndex();
        for (var module : modules.all()) {
            index.add(com.logvex.crystal.commandcenter.ui.SearchEntry.of(
                    com.logvex.crystal.commandcenter.ui.SearchEntry.Category.MODULE,
                    module.name(), module.category().displayName(), "", module.name()));
            for (com.logvex.crystal.setting.SettingGroup group : module.groups()) {
                for (com.logvex.crystal.setting.Setting<?> setting : group.settings()) {
                    index.add(com.logvex.crystal.commandcenter.ui.SearchEntry.of(
                            com.logvex.crystal.commandcenter.ui.SearchEntry.Category.SETTING,
                            setting.name(), module.name() + " \u00B7 " + group.name(), "",
                            module.name()));
                }
            }
        }
        if (commandCenter != null) {
            for (var action : commandCenter.actions().all()) {
                index.add(com.logvex.crystal.commandcenter.ui.SearchEntry.of(
                        com.logvex.crystal.commandcenter.ui.SearchEntry.Category.ACTION,
                        action.label(), action.category() + " \u00B7 " + action.description(),
                        action.id(), ""));
            }
        }
        for (com.logvex.crystal.commandcenter.ui.CommandCenterPreferences.Workspace workspace
                : com.logvex.crystal.commandcenter.ui.CommandCenterPreferences.Workspace.values()) {
            index.add(com.logvex.crystal.commandcenter.ui.SearchEntry.of(
                    com.logvex.crystal.commandcenter.ui.SearchEntry.Category.WORKSPACE,
                    workspace.label(), "Command Center workspace", "", workspace.name()));
        }
        for (String profile : profileService.names()) {
            index.add(com.logvex.crystal.commandcenter.ui.SearchEntry.of(
                    com.logvex.crystal.commandcenter.ui.SearchEntry.Category.PROFILE,
                    profile, "saved profile", "", profile));
        }
        for (String command : COMMAND_HINTS) {
            index.add(com.logvex.crystal.commandcenter.ui.SearchEntry.of(
                    com.logvex.crystal.commandcenter.ui.SearchEntry.Category.COMMAND,
                    command, "command \u00B7 ; " + command, "", command));
        }
        return index;
    }

    /** The common commands the search box can run directly, with their prefixes stripped. */
    private static final java.util.List<String> COMMAND_HINTS = java.util.List.of(
            "center", "center status", "center perf", "center compat", "conflicts", "errors",
            "diag", "modules", "hud", "hudedit", "keybinds", "profile", "favorites", "recent",
            "theme", "appearance", "waypoint", "baritone", "ai", "automation", "world", "entities",
            "notifications", "save", "reload");

    /**
     * The ClickGUI, created on first use.
     *
     * <p>{@code Screen} snapshots {@code MinecraftClient.textRenderer} in its constructor, and
     * that field is still null while {@code onInitializeClient} runs. Building the screen here
     * (once fonts exist) keeps it from being permanently constructed with a null renderer,
     * which would make {@code render()} bail out and draw nothing. The stale-instance guard
     * covers any screen that was nonetheless built early.
     */
    public ClickGui clickGui() {
        if (clickGui == null || !clickGui.isRenderable()) {
            clickGui = new ClickGui(modules, config, hud, theme);
        }
        return clickGui;
    }

    /** The persistent friend list, used to colour ESP and exclude friends from targeting. */
    public com.logvex.crystal.social.FriendList friends() {
        return friends;
    }

    /** Entity lifecycle tracker: spawn/despawn events and bounded history. */
    public com.logvex.crystal.entity.EntityTracker entityTracker() {
        return entityTracker;
    }

    /** The persisted waypoint list. */
    public com.logvex.crystal.util.waypoint.WaypointStore waypoints() {
        return waypoints;
    }

    /** The shared Baritone integration service (always present; availability is probed lazily). */
    public com.logvex.crystal.integration.baritone.BaritoneService baritone() {
        return baritone;
    }

    /** The shared action coordinator every acting module submits through. */
    public com.logvex.crystal.coordination.ActionCoordinator actions() {
        return actionCoordinator;
    }

    /** The read-only module-coordination inspector (dependencies, conflicts, action contention). */
    public com.logvex.crystal.coordination.ModuleCoordinationService coordination() {
        return coordination;
    }

    /** The shared event stream over observable client state changes. */
    public com.logvex.crystal.event.ClientEventBus events() {
        return events;
    }

    /** The automation rule engine. */
    public com.logvex.crystal.automation.AutomationEngine automation() {
        return automation;
    }

    /** Persists the automation rules and enabled flag. */
    public void saveAutomation() {
        if (automationStore == null) {
            return;
        }
        try {
            automationStore.save(automation.rules(), automation.isEnabled());
        } catch (java.io.IOException e) {
            LOG.error("Could not save automation rules", e);
        }
    }

    /** Favourite/recent module tracking for the ClickGUI. */
    public FavoritesStore favorites() {
        return favoritesStore;
    }

    /** Named configuration profiles (save/load/import/export). */
    public ProfileService profiles() {
        return profileService;
    }

    /** Advanced workspaces: selective apply and preview over the profile store. */
    public com.logvex.crystal.workspace.AdvancedWorkspaceService workspaces() {
        return workspaces;
    }

    /** The extension lifecycle manager, or null before bootstrap completes. */
    public com.logvex.crystal.extension.ExtensionManager extensions() {
        return extensionManager;
    }

    /** The stable extension API registry, or null before bootstrap completes. */
    public com.logvex.crystal.api.ApiRegistry apiRegistry() {
        return apiRegistry;
    }

    /** The capabilities available to extensions right now. */
    public com.logvex.crystal.api.Capabilities capabilities() {
        java.util.Set<String> active = new java.util.LinkedHashSet<>();
        active.add(com.logvex.crystal.api.Capabilities.COMBAT);
        active.add(com.logvex.crystal.api.Capabilities.WAYPOINTS);
        active.add(com.logvex.crystal.api.Capabilities.HUD);
        active.add(com.logvex.crystal.api.Capabilities.COMMANDS);
        active.add(com.logvex.crystal.api.Capabilities.EVENTS);
        active.add(com.logvex.crystal.api.Capabilities.MODULES);
        active.add(com.logvex.crystal.api.Capabilities.NOTIFICATIONS);
        active.add(com.logvex.crystal.api.Capabilities.DIAGNOSTICS);
        if (baritone != null && baritone.available()) {
            active.add(com.logvex.crystal.api.Capabilities.BARITONE);
        }
        if (net.minecraft.client.MinecraftClient.getInstance() != null
                && net.minecraft.client.MinecraftClient.getInstance().world != null) {
            active.add(com.logvex.crystal.api.Capabilities.WORLD);
        }
        return new com.logvex.crystal.api.Capabilities(active);
    }

    /** This client's mod version, or {@code dev} when running unmapped. */
    public static String modVersion() {
        return net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer("logvex-crystal")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("dev");
    }

    public void notify(String message, NotificationLevel level) {
        notifications.notify(message, level);
    }

    // ------------------------------------------------------------ mini-AI accessors

    /** The shared mini-AI preferences. */
    public com.logvex.crystal.ai.AISettings aiSettings() {
        return aiSettings;
    }

    /** Persists the mini-AI preferences immediately (used by the settings UI). */
    public void saveAiSettings() {
        if (aiSettingsStore == null) {
            return;
        }
        try {
            aiSettingsStore.save(aiSettings);
        } catch (IOException e) {
            LOG.warn("Could not save ai.json", e);
        }
    }

    /** The local model manager (never null once bootstrap has run). */
    public com.logvex.crystal.ai.MiniAIManager ai() {
        return ai;
    }

    /** The crash registry. */
    public com.logvex.crystal.ai.crash.CrashMonitor crashMonitor() {
        return crashMonitor;
    }

    /** Deterministic crash analysis, with optional model enhancement. */
    public com.logvex.crystal.ai.crash.CrashAnalyzerService crashAnalyzer() {
        return crashAnalyzer;
    }

    /** The report pipeline (queue, approval, send). */
    public com.logvex.crystal.ai.report.ReportService reportService() {
        return reportService;
    }

    /** The latest cached Baritone advice (refreshed at most once a second). */
    public com.logvex.crystal.ai.baritone.BaritoneAdvice baritoneAdvice() {
        return baritoneAdvice;
    }

    /**
     * Re-reads the AI preferences after a change and persists them. Called by the AIAssistant
     * module whenever a setting changes, so a change takes effect immediately and survives a
     * restart.
     */
    public void applyAiSettings() {
        if (ai != null) {
            ai.reconfigure();
        }
        saveAiSettings();
    }

    /** Asks the local model to explain a crash, if local analysis is enabled. */
    public void askAiAboutCrash(com.logvex.crystal.ai.crash.CrashRecord record,
                                com.logvex.crystal.ai.crash.Diagnosis diagnosis) {
        if (ai == null || !aiSettings.localAnalysis) {
            return;
        }
        ai.submitOnce("crash:" + record.fingerprint(), aiPrompts.crashPrompt(record, diagnosis),
                text -> crashAnalyzer.enhance(record.fingerprint(), text));
    }

    /** Asks the local model for Baritone suggestions, if local analysis is enabled. */
    public void askAiAboutBaritone() {
        if (ai == null || !aiSettings.localAnalysis) {
            return;
        }
        com.logvex.crystal.integration.baritone.BaritoneStatus status = baritone.status();
        ai.submitOnce("baritone:" + status.stateKey(),
                aiPrompts.baritonePrompt(status, baritone.lastActionError()),
                text -> LOG.info("Baritone AI suggestion: {}", text));
    }

    /** Downloads the given model in the background, reporting progress through notifications. */
    public void downloadModel(com.logvex.crystal.ai.model.ModelSpec spec) {
        if (ai == null || spec == null) {
            return;
        }
        Thread worker = new Thread(() -> {
            try {
                ai.loader().download(spec, (received, total) -> {
                    // Progress is coarse on purpose: no per-chunk chat spam.
                });
                notifications.success(spec.displayName() + " downloaded \u2661");
            } catch (IOException | RuntimeException e) {
                notifications.error("Download failed: " + e.getMessage());
            }
        }, "logvex-model-download");
        worker.setDaemon(true);
        worker.start();
    }
}
