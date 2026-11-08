package com.logvex.crystal.scan.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests movement arbitration: a single owner, refusal on conflict, and clean release. */
class MovementCoordinatorTest {

    @Test
    void firstClaimSucceedsAndBlocksOthers() {
        MovementCoordinator coordinator = new MovementCoordinator();
        assertTrue(coordinator.free());
        assertNull(coordinator.claim("scanner", "scan", 1L));
        assertTrue(coordinator.holds("scanner"));
        assertEquals("scanner", coordinator.holder());
        assertEquals("scanner", coordinator.claim("killaura", "pvp", 2L));
        assertFalse(coordinator.holds("killaura"));
    }

    @Test
    void sameOwnerCanRefreshItsClaim() {
        MovementCoordinator coordinator = new MovementCoordinator();
        coordinator.claim("scanner", "scan", 1L);
        assertNull(coordinator.claim("scanner", "scan again", 2L));
        assertTrue(coordinator.holds("scanner"));
    }

    @Test
    void releaseFreesMovement() {
        MovementCoordinator coordinator = new MovementCoordinator();
        coordinator.claim("scanner", "scan", 1L);
        assertTrue(coordinator.release("scanner"));
        assertTrue(coordinator.free());
        assertFalse(coordinator.release("scanner"));
        assertNull(coordinator.claim("killaura", "pvp", 3L));
    }

    @Test
    void releaseAllClearsEverything() {
        MovementCoordinator coordinator = new MovementCoordinator();
        coordinator.claim("scanner", "scan", 1L);
        coordinator.releaseAll();
        assertTrue(coordinator.free());
    }

    @Test
    void blankOwnerIsRejected() {
        MovementCoordinator coordinator = new MovementCoordinator();
        assertEquals("invalid owner", coordinator.claim("", "x", 1L));
        assertEquals("invalid owner", coordinator.claim(null, "x", 1L));
    }

    @Test
    void logRecordsArbitration() {
        MovementCoordinator coordinator = new MovementCoordinator();
        coordinator.claim("scanner", "scan", 1L);
        coordinator.release("scanner");
        assertTrue(coordinator.log().size() >= 2);
    }
}
