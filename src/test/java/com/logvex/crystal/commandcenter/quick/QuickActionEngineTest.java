package com.logvex.crystal.commandcenter.quick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.logvex.crystal.commandcenter.ClientStatusModel;
import com.logvex.crystal.commandcenter.HealthStatus;
import com.logvex.crystal.commandcenter.PerformanceReport;
import com.logvex.crystal.commandcenter.StatusEntry;
import com.logvex.crystal.commandcenter.StatusSection;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Covers the quick-action engine's safety policy: the enable gate, rising-edge triggers,
 * per-action cooldown, max executions, the global rate limit and the confirmation flow.
 */
class QuickActionEngineTest {

    /** A fully controllable context. */
    private static final class FakeContext implements QuickActionContext {
        ClientStatusModel status;
        PerformanceReport performance;
        int moduleErrors;
        int crashes;
        boolean baritone = true;
        boolean aiLoaded;
        boolean world = true;

        @Override
        public ClientStatusModel status() {
            return status;
        }

        @Override
        public PerformanceReport performance() {
            return performance;
        }

        @Override
        public int moduleErrorCount() {
            return moduleErrors;
        }

        @Override
        public int crashCount() {
            return crashes;
        }

        @Override
        public boolean baritoneAvailable() {
            return baritone;
        }

        @Override
        public boolean aiLoaded() {
            return aiLoaded;
        }

        @Override
        public boolean worldAvailable() {
            return world;
        }
    }

    /** Records every execution and confirmation request. */
    private static final class FakeHandler implements QuickActionHandler {
        final List<String> executed = new ArrayList<>();
        final List<String> confirmations = new ArrayList<>();
        boolean throwOnExecute;

        @Override
        public String execute(QuickAction action) {
            if (throwOnExecute) {
                throw new IllegalStateException("boom");
            }
            executed.add(action.id());
            return "ran " + action.name();
        }

        @Override
        public void requestConfirmation(QuickAction action) {
            confirmations.add(action.id());
        }
    }

    private static ClientStatusModel statusWithProblem() {
        return new ClientStatusModel(List.of(
                StatusSection.of("modules", "Modules",
                        StatusEntry.attention("Sprint", "error", "NPE"))), 1L);
    }

    private static ClientStatusModel healthyStatus() {
        return new ClientStatusModel(List.of(
                StatusSection.of("modules", "Modules", StatusEntry.ok("Sprint", "on"))), 1L);
    }

    private static PerformanceReport degraded() {
        return new PerformanceReport(com.logvex.crystal.commandcenter.PerformanceSample.empty(1L),
                List.of(new com.logvex.crystal.commandcenter.PerformanceFinding("fps",
                        HealthStatus.ATTENTION, "fps is low", "close some modules", "fps")));
    }

    private QuickAction enabledAction(String id, QuickTrigger trigger, QuickActionType type) {
        QuickAction action = new QuickAction(id, id);
        action.enabled(true).trigger(trigger).type(type).argument("Sprint").cooldownMillis(0);
        return action;
    }

    @Test
    void nothingRunsWhileDisabled() {
        FakeContext context = new FakeContext();
        context.status = statusWithProblem();
        FakeHandler handler = new FakeHandler();
        QuickActionEngine engine = new QuickActionEngine(handler);
        engine.upsert(enabledAction("a", QuickTrigger.PROBLEM_APPEARS, QuickActionType.TOGGLE_MODULE));
        assertEquals(0, engine.tick(context, 1000));
        assertTrue(handler.executed.isEmpty());
    }

    @Test
    void edgeTriggerFiresOnceThenRearmsWhenCleared() {
        FakeContext context = new FakeContext();
        context.status = statusWithProblem();
        FakeHandler handler = new FakeHandler();
        QuickActionEngine engine = new QuickActionEngine(handler);
        engine.setEnabled(true);
        engine.upsert(enabledAction("a", QuickTrigger.PROBLEM_APPEARS, QuickActionType.TOGGLE_MODULE));

        assertEquals(1, engine.tick(context, 1000));
        // Still holding: no second firing.
        assertEquals(0, engine.tick(context, 1100));
        // Clears, then recurs: a fresh rising edge.
        context.status = healthyStatus();
        engine.tick(context, 1200);
        context.status = statusWithProblem();
        assertEquals(1, engine.tick(context, 1300));
        assertEquals(2, handler.executed.size());
    }

    @Test
    void cooldownSuppressesRepeatedFirings() {
        FakeContext context = new FakeContext();
        context.crashes = 1;
        FakeHandler handler = new FakeHandler();
        QuickActionEngine engine = new QuickActionEngine(handler);
        engine.setEnabled(true);
        QuickAction action = enabledAction("a", QuickTrigger.CRASH_RECORDED, QuickActionType.OPEN_WORKSPACE);
        action.cooldownMillis(10_000);
        engine.upsert(action);

        assertEquals(1, engine.tick(context, 1000));
        // A crash trigger is edge-based, so clear and recur within the cooldown.
        context.crashes = 0;
        engine.tick(context, 2000);
        context.crashes = 1;
        assertEquals(0, engine.tick(context, 3000));
        assertEquals(1, handler.executed.size());
        assertTrue(engine.suppressedByCooldown() >= 1);
    }

    @Test
    void maxExecutionsStopsARunawayAction() {
        FakeContext context = new FakeContext();
        FakeHandler handler = new FakeHandler();
        QuickActionEngine engine = new QuickActionEngine(handler);
        engine.setEnabled(true);
        QuickAction action = enabledAction("a", QuickTrigger.MANUAL, QuickActionType.SHOW_DIAGNOSTIC);
        action.message("hi").maxExecutions(2).cooldownMillis(0);
        engine.upsert(action);

        assertTrue(engine.runManually("a", 1000));
        assertTrue(engine.runManually("a", 2000));
        assertFalse(engine.runManually("a", 3000));
        assertEquals(2, handler.executed.size());
    }

    @Test
    void globalRateLimitCapsFiringsPerMinute() {
        FakeContext context = new FakeContext();
        FakeHandler handler = new FakeHandler();
        QuickActionEngine engine = new QuickActionEngine(handler);
        engine.setEnabled(true);
        engine.setMaxFiringsPerMinute(3);
        QuickAction action = enabledAction("a", QuickTrigger.MANUAL, QuickActionType.SHOW_DIAGNOSTIC);
        action.message("hi").cooldownMillis(0);
        engine.upsert(action);

        for (int i = 0; i < 5; i++) {
            engine.runManually("a", 1000 + i);
        }
        assertEquals(3, handler.executed.size());
        assertTrue(engine.suppressedByRateLimit() >= 1);
    }

    @Test
    void mutatingActionRequiresConfirmationBeforeItRuns() {
        FakeContext context = new FakeContext();
        FakeHandler handler = new FakeHandler();
        QuickActionEngine engine = new QuickActionEngine(handler);
        engine.setEnabled(true);
        QuickAction action = enabledAction("a", QuickTrigger.MANUAL, QuickActionType.TOGGLE_MODULE)
                .requiresConfirmation(true);
        engine.upsert(action);

        assertTrue(engine.runManually("a", 1000));
        assertTrue(handler.executed.isEmpty());
        assertTrue(engine.awaitingConfirmation("a"));
        assertEquals(1, handler.confirmations.size());

        assertTrue(engine.approve("a", 1100));
        assertEquals(1, handler.executed.size());
        assertFalse(engine.awaitingConfirmation("a"));
    }

    @Test
    void declineCancelsAPendingConfirmation() {
        FakeContext context = new FakeContext();
        FakeHandler handler = new FakeHandler();
        QuickActionEngine engine = new QuickActionEngine(handler);
        engine.setEnabled(true);
        engine.upsert(enabledAction("a", QuickTrigger.MANUAL, QuickActionType.SWITCH_PROFILE)
                .requiresConfirmation(true));

        engine.runManually("a", 1000);
        engine.decline("a");
        assertFalse(engine.awaitingConfirmation("a"));
        assertFalse(engine.approve("a", 1100));
        assertTrue(handler.executed.isEmpty());
    }

    @Test
    void aThrowingHandlerIsRecordedAsAFailureNotPropagated() {
        FakeContext context = new FakeContext();
        FakeHandler handler = new FakeHandler();
        handler.throwOnExecute = true;
        QuickActionEngine engine = new QuickActionEngine(handler);
        engine.setEnabled(true);
        engine.upsert(enabledAction("a", QuickTrigger.MANUAL, QuickActionType.OPEN_WORKSPACE));

        engine.runManually("a", 1000);
        List<QuickActionEngine.Execution> history = engine.history();
        assertEquals(1, history.size());
        assertFalse(history.get(0).success());
        assertTrue(history.get(0).outcome().contains("IllegalStateException"));
    }

    @Test
    void executionHistoryIsBounded() {
        FakeContext context = new FakeContext();
        FakeHandler handler = new FakeHandler();
        QuickActionEngine engine = new QuickActionEngine(handler);
        engine.setEnabled(true);
        engine.setMaxFiringsPerMinute(1000);
        QuickAction action = enabledAction("a", QuickTrigger.MANUAL, QuickActionType.SHOW_DIAGNOSTIC);
        action.message("hi").cooldownMillis(0).maxExecutions(0);
        engine.upsert(action);

        for (int i = 0; i < 200; i++) {
            engine.runManually("a", 1000 + i);
        }
        assertTrue(engine.history().size() <= 64);
    }

    @Test
    void integrationTriggerFiresWhenBaritoneIsMissing() {
        FakeContext context = new FakeContext();
        context.baritone = false;
        FakeHandler handler = new FakeHandler();
        QuickActionEngine engine = new QuickActionEngine(handler);
        engine.setEnabled(true);
        engine.upsert(enabledAction("a", QuickTrigger.INTEGRATION_UNAVAILABLE,
                QuickActionType.SHOW_DIAGNOSTIC).message("Baritone missing"));

        assertEquals(1, engine.tick(context, 1000));
        assertEquals(1, handler.executed.size());
    }

    @Test
    void performanceTriggerFiresOnTheRisingEdgeOfDegradation() {
        FakeContext context = new FakeContext();
        context.performance = new PerformanceReport(
                com.logvex.crystal.commandcenter.PerformanceSample.empty(1L), List.of());
        FakeHandler handler = new FakeHandler();
        QuickActionEngine engine = new QuickActionEngine(handler);
        engine.setEnabled(true);
        engine.upsert(enabledAction("a", QuickTrigger.PERFORMANCE_DEGRADED, QuickActionType.SHOW_DIAGNOSTIC)
                .message("Slow down"));

        assertEquals(0, engine.tick(context, 1000)); // healthy
        context.performance = degraded();
        assertEquals(1, engine.tick(context, 2000));
        assertEquals(0, engine.tick(context, 3000)); // still degraded, no re-fire
        assertEquals(1, handler.executed.size());
    }

    @Test
    void manualTriggerNeverFiresOnItsOwn() {
        FakeContext context = new FakeContext();
        context.status = statusWithProblem();
        FakeHandler handler = new FakeHandler();
        QuickActionEngine engine = new QuickActionEngine(handler);
        engine.setEnabled(true);
        engine.upsert(enabledAction("a", QuickTrigger.MANUAL, QuickActionType.TOGGLE_MODULE));

        assertEquals(0, engine.tick(context, 1000));
        assertTrue(handler.executed.isEmpty());
        assertTrue(engine.runManually("a", 1100));
        assertEquals(1, handler.executed.size());
    }
}
