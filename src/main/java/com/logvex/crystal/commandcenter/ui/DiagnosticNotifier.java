package com.logvex.crystal.commandcenter.ui;

import com.logvex.crystal.commandcenter.ClientStatusModel;
import com.logvex.crystal.commandcenter.HealthStatus;
import com.logvex.crystal.commandcenter.StatusEntry;
import com.logvex.crystal.notification.NotificationLevel;
import com.logvex.crystal.notification.NotificationManager;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Turns the Command Center's status model into user notifications, without ever becoming a
 * source of spam.
 *
 * <p>Left to itself a status model would notify on every refresh, and a persistently broken
 * client would flood the screen. This notifier is therefore conservative by construction:
 * <ul>
 *   <li><b>Deduplication</b> - each distinct problem is announced once and not again until its
 *       cooldown has elapsed.</li>
 *   <li><b>Per-problem cooldown</b> - a configurable minimum gap between repeats of the same
 *       problem.</li>
 *   <li><b>Global rate limit</b> - a hard ceiling on notices per minute, so a burst of new
 *       problems cannot drown out the HUD.</li>
 *   <li><b>Recovery notice</b> - a problem that clears is announced once, so the user learns it
 *       is resolved.</li>
 * </ul>
 *
 * <p>Minecraft-independent: it takes a {@link NotificationManager} (itself game-free) and the
 * clock, so the whole policy is unit tested with a fake clock.
 */
public final class DiagnosticNotifier {

    private static final long RATE_WINDOW_MILLIS = 60_000L;
    private static final int MAX_TRACKED = 64;

    private final NotificationManager notifications;
    private final Map<String, Long> lastNotifiedAt = new HashMap<>();
    private final Deque<Long> recentNotices = new ArrayDeque<>();
    private CommandCenterPreferences preferences;

    public DiagnosticNotifier(NotificationManager notifications, CommandCenterPreferences preferences) {
        this.notifications = notifications;
        this.preferences = preferences == null ? new CommandCenterPreferences() : preferences;
    }

    public void setPreferences(CommandCenterPreferences preferences) {
        if (preferences != null) {
            this.preferences = preferences;
        }
    }

    /**
     * Inspects {@code model} and raises at most one notification for the problems it has not
     * already announced. Call this when the status snapshot is refreshed, not every frame.
     *
     * @return how many notifications were raised
     */
    public int observe(ClientStatusModel model, long now) {
        if (model == null || notifications == null || !preferences.notificationsEnabled) {
            return 0;
        }
        int raised = 0;
        for (StatusEntry problem : model.problems()) {
            String key = key(problem);
            if (suppressed(key, now)) {
                continue;
            }
            if (rateLimited(now)) {
                continue;
            }
            NotificationLevel level = problem.status() == HealthStatus.CRITICAL
                    ? NotificationLevel.ERROR : NotificationLevel.WARN;
            notifications.notify("\u2661 " + problem.label() + ": " + problem.value(), level);
            lastNotifiedAt.put(key, now);
            recentNotices.addLast(now);
            raised++;
        }
        prune(now);
        return raised;
    }

    /** Announces that a previously seen problem has cleared, at most once. */
    public void observeRecovery(ClientStatusModel model, long now) {
        if (model == null || notifications == null || !preferences.notificationsEnabled) {
            return;
        }
        // A key that has been notified but is no longer a problem has recovered.
        for (Map.Entry<String, Long> entry : new HashMap<>(lastNotifiedAt).entrySet()) {
            if (stillProblem(model, entry.getKey())) {
                continue;
            }
            lastNotifiedAt.remove(entry.getKey());
            if (rateLimited(now)) {
                continue;
            }
            notifications.notify("\u2661 recovered: " + entry.getKey(), NotificationLevel.SUCCESS);
            recentNotices.addLast(now);
        }
    }

    private boolean stillProblem(ClientStatusModel model, String key) {
        for (StatusEntry problem : model.problems()) {
            if (key(problem).equals(key)) {
                return true;
            }
        }
        return false;
    }

    private boolean suppressed(String key, long now) {
        Long last = lastNotifiedAt.get(key);
        if (last == null) {
            return false;
        }
        if (!preferences.dedupeNotifications) {
            return false;
        }
        return now - last < preferences.notificationCooldownMillis;
    }

    private boolean rateLimited(long now) {
        while (!recentNotices.isEmpty() && now - recentNotices.peekFirst() > RATE_WINDOW_MILLIS) {
            recentNotices.removeFirst();
        }
        return recentNotices.size() >= preferences.notificationRateLimit;
    }

    private void prune(long now) {
        if (lastNotifiedAt.size() <= MAX_TRACKED) {
            return;
        }
        lastNotifiedAt.entrySet().removeIf(e -> now - e.getValue() > preferences.notificationCooldownMillis * 4);
    }

    /** Forgets all tracked problems (used by the Notifications workspace's reset action). */
    public void reset() {
        lastNotifiedAt.clear();
        recentNotices.clear();
    }

    /** How many distinct problems are currently being tracked as announced. */
    public int trackedCount() {
        return lastNotifiedAt.size();
    }

    private static String key(StatusEntry problem) {
        return problem.label() + "|" + problem.value();
    }
}
