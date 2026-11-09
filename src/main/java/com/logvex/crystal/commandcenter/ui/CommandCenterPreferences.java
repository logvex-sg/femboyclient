package com.logvex.crystal.commandcenter.ui;

/**
 * The Command Center's own preferences, separate from the theme and the module config.
 *
 * <p>These control how the Command Center behaves and looks (default workspace, refresh
 * intervals, notification behaviour, whether quick actions may change state without a
 * prompt) and, together with the {@link DashboardLayout}, are persisted to
 * {@code command-center.json}. Plain mutable fields with a {@link #clamp()} so a hand-edited
 * file cannot put the UI into an impossible state, and a {@link #copy()} so it can be
 * snapshotted into a profile.
 *
 * <p>Minecraft-independent and unit testable.
 */
public final class CommandCenterPreferences {

    /** The workspaces the user can pick as the one the screen opens on. */
    public enum Workspace {
        DASHBOARD("Dashboard"),
        AI("AI assistant"),
        MODULES("Modules"),
        PERFORMANCE("Performance"),
        BARITONE("Baritone"),
        CONFIG("Configuration"),
        CRASHES("Crashes"),
        NOTIFICATIONS("Notifications"),
        QUICK_ACTIONS("Quick actions");

        private final String label;

        Workspace(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public static Workspace parse(String value) {
            if (value == null) {
                return DASHBOARD;
            }
            String needle = value.trim().toUpperCase(java.util.Locale.ROOT);
            for (Workspace workspace : values()) {
                if (workspace.name().equals(needle)) {
                    return workspace;
                }
            }
            return DASHBOARD;
        }
    }

    /** The workspace the screen opens on. */
    public Workspace defaultWorkspace = Workspace.DASHBOARD;

    /** How often the status snapshot is refreshed while the screen is open (ms). Clamped 250-10000. */
    public long refreshIntervalMillis = 1000L;

    /** How often a performance-history sample is taken (ms). Clamped 250-30000. */
    public long historyIntervalMillis = 1000L;

    /** Whether the compact HUD readout shows the top performance finding. */
    public boolean hudShowFindings = true;

    /** Whether the Command Center raises notifications for new problems it notices. */
    public boolean notificationsEnabled = true;

    /** Whether notifications are deduplicated (an identical problem is announced once). */
    public boolean dedupeNotifications = true;

    /** Minimum gap between two notifications about the same problem, in milliseconds. */
    public long notificationCooldownMillis = 30_000L;

    /** Hard ceiling on notifications the Command Center may raise per minute. */
    public int notificationRateLimit = 6;

    /**
     * When true, quick actions that change state (enable/disable a module, switch a profile)
     * run without an extra prompt. When false (the default) the user confirms each one.
     */
    public boolean autoApplyQuickActions = false;

    /** Whether the reduced-motion hint is honoured for the Command Center's own animations. */
    public boolean reducedMotion = false;

    public void clamp() {
        refreshIntervalMillis = clamp(refreshIntervalMillis, 250L, 10_000L);
        historyIntervalMillis = clamp(historyIntervalMillis, 250L, 30_000L);
        notificationCooldownMillis = clamp(notificationCooldownMillis, 1000L, 600_000L);
        notificationRateLimit = (int) clamp(notificationRateLimit, 1L, 60L);
        if (defaultWorkspace == null) {
            defaultWorkspace = Workspace.DASHBOARD;
        }
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    public CommandCenterPreferences copy() {
        CommandCenterPreferences copy = new CommandCenterPreferences();
        copy.defaultWorkspace = defaultWorkspace;
        copy.refreshIntervalMillis = refreshIntervalMillis;
        copy.historyIntervalMillis = historyIntervalMillis;
        copy.hudShowFindings = hudShowFindings;
        copy.notificationsEnabled = notificationsEnabled;
        copy.dedupeNotifications = dedupeNotifications;
        copy.notificationCooldownMillis = notificationCooldownMillis;
        copy.notificationRateLimit = notificationRateLimit;
        copy.autoApplyQuickActions = autoApplyQuickActions;
        copy.reducedMotion = reducedMotion;
        return copy;
    }
}
