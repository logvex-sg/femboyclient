package com.logvex.crystal.commandcenter.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.logvex.crystal.commandcenter.ClientStatusModel;
import com.logvex.crystal.commandcenter.StatusEntry;
import com.logvex.crystal.commandcenter.StatusSection;
import com.logvex.crystal.notification.NotificationManager;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers the diagnostic notifier's spam controls: dedupe, cooldown, rate limit and recovery. */
class DiagnosticNotifierTest {

    private static ClientStatusModel modelWith(String label) {
        return new ClientStatusModel(List.of(
                StatusSection.of("modules", "Modules", StatusEntry.attention(label, "error", "NPE"))), 1L);
    }

    @Test
    void aProblemIsAnnouncedOnceThenDeduplicated() {
        NotificationManager notifications = new NotificationManager();
        DiagnosticNotifier notifier = new DiagnosticNotifier(notifications,
                new CommandCenterPreferences());
        assertEquals(1, notifier.observe(modelWith("Sprint"), 1000));
        // Same problem within the cooldown: suppressed.
        assertEquals(0, notifier.observe(modelWith("Sprint"), 2000));
        assertEquals(1, notifier.trackedCount());
    }

    @Test
    void aProblemIsReAnnouncedAfterTheCooldown() {
        CommandCenterPreferences prefs = new CommandCenterPreferences();
        prefs.notificationCooldownMillis = 5000;
        NotificationManager notifications = new NotificationManager();
        DiagnosticNotifier notifier = new DiagnosticNotifier(notifications, prefs);
        assertEquals(1, notifier.observe(modelWith("Sprint"), 1000));
        assertEquals(1, notifier.observe(modelWith("Sprint"), 7000));
    }

    @Test
    void theGlobalRateLimitCapsABurst() {
        CommandCenterPreferences prefs = new CommandCenterPreferences();
        prefs.notificationRateLimit = 2;
        prefs.notificationCooldownMillis = 0;
        NotificationManager notifications = new NotificationManager();
        DiagnosticNotifier notifier = new DiagnosticNotifier(notifications, prefs);
        // Three distinct problems at the same instant; only two may be announced.
        int first = notifier.observe(new ClientStatusModel(List.of(StatusSection.of("m", "M",
                StatusEntry.attention("A", "e", ""), StatusEntry.attention("B", "e", ""),
                StatusEntry.attention("C", "e", ""))), 1L), 1000);
        assertEquals(2, first);
    }

    @Test
    void notificationsCanBeDisabled() {
        CommandCenterPreferences prefs = new CommandCenterPreferences();
        prefs.notificationsEnabled = false;
        NotificationManager notifications = new NotificationManager();
        DiagnosticNotifier notifier = new DiagnosticNotifier(notifications, prefs);
        assertEquals(0, notifier.observe(modelWith("Sprint"), 1000));
    }

    @Test
    void recoveryClearsTrackedProblems() {
        NotificationManager notifications = new NotificationManager();
        DiagnosticNotifier notifier = new DiagnosticNotifier(notifications,
                new CommandCenterPreferences());
        notifier.observe(modelWith("Sprint"), 1000);
        assertEquals(1, notifier.trackedCount());
        ClientStatusModel healthy = new ClientStatusModel(List.of(
                StatusSection.of("modules", "Modules", StatusEntry.ok("Sprint", "on"))), 1L);
        notifier.observeRecovery(healthy, 2000);
        assertEquals(0, notifier.trackedCount());
        assertTrue(notifications.history().stream()
                .anyMatch(n -> n.text().contains("recovered")));
    }

    @Test
    void resetForgetsEverything() {
        NotificationManager notifications = new NotificationManager();
        DiagnosticNotifier notifier = new DiagnosticNotifier(notifications,
                new CommandCenterPreferences());
        notifier.observe(modelWith("Sprint"), 1000);
        notifier.reset();
        assertEquals(0, notifier.trackedCount());
    }
}
