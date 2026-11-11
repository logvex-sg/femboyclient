package com.logvex.crystal.automation.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** Covers one-click-per-tick sequencing, verification, timeout, arbitration and cancellation. */
class InventoryTransactionManagerTest {

    @Test
    void runsOneStepPerTickAndVerifiesEachBeforeTheNext() {
        InventoryTransactionManager manager = new InventoryTransactionManager();
        List<String> clicked = new ArrayList<>();
        AtomicBoolean step1Done = new AtomicBoolean();
        AtomicBoolean step2Done = new AtomicBoolean();
        InventoryTransaction transaction = InventoryTransaction.builder("mod", "swap")
                .step("click 1", () -> clicked.add("1"), step1Done::get)
                .step("click 2", () -> clicked.add("2"), step2Done::get)
                .build();
        assertTrue(manager.submit(transaction));

        assertTrue(manager.tick(0));   // issues click 1
        assertFalse(manager.tick(1));  // waits for verification
        assertEquals(List.of("1"), clicked);

        step1Done.set(true);
        assertTrue(manager.tick(2));   // click 1 verified -> issues click 2
        assertEquals(List.of("1", "2"), clicked);

        step2Done.set(true);
        assertFalse(manager.tick(3));  // all verified -> completes
        assertEquals(InventoryTransactionManager.Outcome.COMPLETED,
                manager.history().get(0).outcome());
    }

    @Test
    void onlyOneTransactionRunsAtATime() {
        InventoryTransactionManager manager = new InventoryTransactionManager();
        List<String> ran = new ArrayList<>();
        manager.submit(InventoryTransaction.builder("a", "A")
                .step("a", () -> ran.add("a"), () -> false).build());
        manager.submit(InventoryTransaction.builder("b", "B")
                .step("b", () -> ran.add("b"), () -> false).build());
        manager.tick(0);
        assertEquals(List.of("a"), ran);
        assertTrue(manager.isActive("b"));
        assertFalse(manager.isActive("a") && manager.running().isEmpty());
    }

    @Test
    void aStepThatNeverConfirmsFailsTheTransaction() {
        InventoryTransactionManager manager = new InventoryTransactionManager();
        manager.submit(InventoryTransaction.builder("mod", "stuck")
                .step("click", () -> { }, () -> false).timeoutMillis(10_000).build());
        manager.tick(0);
        manager.tick(800); // past the per-step confirmation timeout
        assertEquals(InventoryTransactionManager.Outcome.FAILED,
                manager.history().get(0).outcome());
    }

    @Test
    void timeoutAbandonsAStuckTransaction() {
        InventoryTransactionManager manager = new InventoryTransactionManager();
        manager.submit(InventoryTransaction.builder("mod", "slow")
                .step("click", () -> { }, () -> false).timeoutMillis(300).build());
        manager.tick(0);
        manager.tick(400);
        assertEquals(InventoryTransactionManager.Outcome.TIMEOUT,
                manager.history().get(0).outcome());
    }

    @Test
    void cancelOwnerStopsOnlyThatOwnersWork() {
        InventoryTransactionManager manager = new InventoryTransactionManager();
        manager.submit(InventoryTransaction.builder("a", "A").step("a", () -> { }, () -> false).build());
        manager.submit(InventoryTransaction.builder("b", "B").step("b", () -> { }, () -> false).build());
        assertEquals(1, manager.cancelOwner("a", 0));
        assertFalse(manager.isActive("a"));
        assertTrue(manager.isActive("b"));
    }

    @Test
    void cancelAllSuspendsUntilResumed() {
        InventoryTransactionManager manager = new InventoryTransactionManager();
        manager.submit(InventoryTransaction.builder("a", "A").step("a", () -> { }, () -> false).build());
        manager.cancelAll(0, "panic");
        assertTrue(manager.isSuspended());
        assertFalse(manager.submit(InventoryTransaction.builder("b", "B")
                .step("b", () -> { }, () -> false).build()), "a suspended manager refuses work");
        manager.resume();
        assertTrue(manager.submit(InventoryTransaction.builder("b", "B")
                .step("b", () -> { }, () -> false).build()));
        manager.tick(2);
        assertTrue(manager.isActive("b"));
    }

    @Test
    void oneOwnerCannotQueueTwoTransactionsAtOnce() {
        InventoryTransactionManager manager = new InventoryTransactionManager();
        assertTrue(manager.submit(InventoryTransaction.builder("a", "A")
                .step("a", () -> { }, () -> false).build()));
        assertFalse(manager.submit(InventoryTransaction.builder("a", "A2")
                .step("a", () -> { }, () -> false).build()));
    }
}
