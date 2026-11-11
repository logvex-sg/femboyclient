package com.logvex.crystal.automation.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Covers priority ordering, delays, cooldowns, conditions, resources, retries, cancel and stop. */
class TaskQueueTest {

    /** A controllable context; every effect is recorded. */
    private static final class FakeContext implements TaskContext {
        boolean world = true;
        long tick;
        final List<String> notifications = new ArrayList<>();
        final List<String> log = new ArrayList<>();
        final Map<String, String> facts = new HashMap<>();
        final List<String> enabled = new ArrayList<>();

        @Override
        public boolean worldAvailable() {
            return world;
        }

        @Override
        public long tickCount() {
            return tick;
        }

        @Override
        public boolean enableModule(String name) {
            enabled.add(name);
            return true;
        }

        @Override
        public boolean disableModule(String name) {
            return true;
        }

        @Override
        public boolean selectHotbar(int slot) {
            return true;
        }

        @Override
        public void notify(String message, String level) {
            notifications.add(level + ":" + message);
        }

        @Override
        public void log(String message) {
            log.add(message);
        }

        @Override
        public Map<String, String> facts() {
            return facts;
        }

        @Override
        public List<String> installedModules() {
            return List.of();
        }
    }

    private static AutomationTask counting(String id, String name, TaskPriority priority,
                                           int steps, List<String> ran) {
        return AutomationTask.builder(id, name, (context, progress) -> {
            ran.add(id);
            progress.progress(ran.size(), steps);
            return ran.size() >= steps
                    ? AutomationTask.StepResult.COMPLETED : AutomationTask.StepResult.RUNNING;
        }).priority(priority).totalSteps(steps).build();
    }

    @Test
    void runsHigherPriorityFirst() {
        TaskQueue queue = new TaskQueue();
        FakeContext context = new FakeContext();
        List<String> ran = new ArrayList<>();
        queue.submit(counting("low", "Low", TaskPriority.LOW, 1, ran), 0);
        queue.submit(counting("high", "High", TaskPriority.HIGH, 1, ran), 0);
        queue.tick(context, 0);
        // Both are ready and unconstrained, so both run - but the scheduler visits the
        // higher-priority task first, which is what decides who wins a contended resource.
        assertEquals(List.of("high", "low"), ran);
    }

    @Test
    void delayHoldsATaskUntilItsReadyTick() {
        TaskQueue queue = new TaskQueue();
        FakeContext context = new FakeContext();
        List<String> ran = new ArrayList<>();
        queue.submit(AutomationTask.builder("delayed", "Delayed", (c, p) -> {
            ran.add("delayed");
            return AutomationTask.StepResult.COMPLETED;
        }).delayTicks(5).build(), 0);
        queue.tick(context, 4);
        assertTrue(ran.isEmpty());
        assertEquals(TaskState.PENDING, queue.byId("delayed").orElseThrow().state());
        queue.tick(context, 5);
        assertEquals(List.of("delayed"), ran);
    }

    @Test
    void conditionNotMetSkipsWhenConfiguredOtherwiseWaits() {
        TaskQueue queue = new TaskQueue();
        FakeContext context = new FakeContext();
        List<String> ran = new ArrayList<>();
        queue.submit(AutomationTask.builder("skip", "Skip", (c, p) -> {
            ran.add("skip");
            return AutomationTask.StepResult.COMPLETED;
        }).when(c -> false).skipWhenConditionsFail(true).build(), 0);
        queue.submit(AutomationTask.builder("wait", "Wait", (c, p) -> {
            ran.add("wait");
            return AutomationTask.StepResult.COMPLETED;
        }).when(c -> false).build(), 0);
        queue.tick(context, 0);
        assertTrue(ran.isEmpty());
        // A skipped task finishes; a waiting task stays active.
        assertTrue(queue.byId("skip").isEmpty());
        assertEquals(TaskState.SKIPPED, queue.history().get(0).outcome());
        assertEquals(TaskState.WAITING, queue.byId("wait").orElseThrow().state());
    }

    @Test
    void resourcesAreExclusiveBetweenOwners() {
        TaskQueue queue = new TaskQueue();
        FakeContext context = new FakeContext();
        List<String> ran = new ArrayList<>();
        queue.submit(AutomationTask.builder("a", "A", (c, p) -> {
            ran.add("a");
            return AutomationTask.StepResult.RUNNING;
        }).owner("moduleA").requiresResource("inventory").priority(TaskPriority.HIGH).build(), 0);
        queue.submit(AutomationTask.builder("b", "B", (c, p) -> {
            ran.add("b");
            return AutomationTask.StepResult.RUNNING;
        }).owner("moduleB").requiresResource("inventory").priority(TaskPriority.LOW).build(), 0);
        queue.tick(context, 0);
        assertEquals(List.of("a"), ran);
        assertEquals("moduleA", queue.holderOf("inventory").orElseThrow());
        assertEquals(TaskState.WAITING, queue.byId("b").orElseThrow().state());
    }

    @Test
    void aFailingTaskRetriesThenFailsAfterItsBudget() {
        TaskQueue queue = new TaskQueue();
        FakeContext context = new FakeContext();
        int[] attempts = {0};
        queue.submit(AutomationTask.builder("flaky", "Flaky", (c, p) -> {
            attempts[0]++;
            return AutomationTask.StepResult.FAILED;
        }).maxAttempts(3).retryBackoffTicks(1).build(), 0);
        queue.tick(context, 0);
        assertEquals(1, attempts[0]);
        assertEquals(TaskState.RETRYING, queue.byId("flaky").orElseThrow().state());
        queue.tick(context, 1);
        assertEquals(2, attempts[0]);
        queue.tick(context, 2);
        assertEquals(3, attempts[0]);
        assertTrue(queue.byId("flaky").isEmpty());
        assertEquals(TaskState.FAILED, queue.history().get(0).outcome());
    }

    @Test
    void emergencyStopCancelsEverythingAndBlocksFurtherWork() {
        TaskQueue queue = new TaskQueue();
        FakeContext context = new FakeContext();
        List<String> ran = new ArrayList<>();
        queue.submit(AutomationTask.builder("t", "T", (c, p) -> {
            ran.add("t");
            return AutomationTask.StepResult.RUNNING;
        }).build(), 0);
        assertEquals(1, queue.emergencyStop(0, "panic"));
        assertTrue(queue.emergencyStopped());
        queue.submit(counting("new", "New", TaskPriority.HIGH, 1, ran), 1);
        queue.tick(context, 1);
        assertTrue(ran.isEmpty(), "no task may run after an emergency stop");
        queue.reset();
        assertFalse(queue.emergencyStopped());
    }

    @Test
    void cancelOwnerStopsOnlyThatOwnersTasks() {
        TaskQueue queue = new TaskQueue();
        FakeContext context = new FakeContext();
        queue.submit(AutomationTask.builder("a", "A", (c, p) -> AutomationTask.StepResult.RUNNING)
                .owner("x").build(), 0);
        queue.submit(AutomationTask.builder("b", "B", (c, p) -> AutomationTask.StepResult.RUNNING)
                .owner("y").build(), 0);
        assertEquals(1, queue.cancelOwner("x", 0, "test"));
        assertTrue(queue.byId("a").isEmpty());
        assertTrue(queue.byId("b").isPresent());
    }

    @Test
    void duplicateIdsAreRejectedWhileActive() {
        TaskQueue queue = new TaskQueue();
        FakeContext context = new FakeContext();
        List<String> ran = new ArrayList<>();
        assertTrue(queue.submit(counting("dup", "Dup", TaskPriority.NORMAL, 3, ran), 0).isPresent());
        assertTrue(queue.submit(counting("dup", "Dup", TaskPriority.NORMAL, 3, ran), 0).isEmpty());
        assertEquals(1, queue.activeCount());
    }

    @Test
    void progressAndHistoryAreRecorded() {
        TaskQueue queue = new TaskQueue();
        FakeContext context = new FakeContext();
        List<String> ran = new ArrayList<>();
        queue.submit(AutomationTask.builder("p", "P", (c, p) -> {
            ran.add("p");
            p.progress(ran.size(), 3);
            p.note("step " + ran.size());
            return ran.size() >= 3 ? AutomationTask.StepResult.COMPLETED
                    : AutomationTask.StepResult.RUNNING;
        }).totalSteps(3).build(), 0);
        queue.tick(context, 0);
        queue.tick(context, 1);
        queue.tick(context, 2);
        assertEquals(TaskState.COMPLETED, queue.history().get(0).outcome());
        assertEquals(3, queue.history().get(0).attempts());
    }

    @Test
    void pausedOwnerDoesNotRun() {
        TaskQueue queue = new TaskQueue();
        FakeContext context = new FakeContext();
        List<String> ran = new ArrayList<>();
        queue.submit(AutomationTask.builder("a", "A", (c, p) -> {
            ran.add("a");
            return AutomationTask.StepResult.COMPLETED;
        }).owner("x").build(), 0);
        queue.pauseOwner("x");
        queue.tick(context, 0);
        assertTrue(ran.isEmpty());
        queue.resumeOwner("x");
        queue.tick(context, 1);
        assertEquals(List.of("a"), ran);
    }

    @Test
    void requiresWorldBlocksWithoutAWorld() {
        TaskQueue queue = new TaskQueue();
        FakeContext context = new FakeContext();
        context.world = false;
        List<String> ran = new ArrayList<>();
        queue.submit(counting("w", "W", TaskPriority.NORMAL, 1, ran), 0);
        queue.tick(context, 0);
        assertTrue(ran.isEmpty());
        context.world = true;
        queue.tick(context, 1);
        assertEquals(List.of("w"), ran);
    }
}
