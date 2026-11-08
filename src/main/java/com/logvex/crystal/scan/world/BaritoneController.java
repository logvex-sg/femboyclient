package com.logvex.crystal.scan.world;

import com.logvex.crystal.integration.baritone.BaritoneGoal;
import com.logvex.crystal.integration.baritone.BaritoneService;
import com.logvex.crystal.integration.baritone.BaritoneStatus;

/**
 * The scanner's one door to Baritone.
 *
 * <p>Baritone is an optional dependency reached reflectively by {@link BaritoneService}, so this
 * controller never assumes it exists: every method checks {@link BaritoneService#available()} and
 * degrades to a no-op with a clear reason. It reports navigation status and goal progress for the
 * dashboard, issues and cancels goals, and supports pause/resume where Baritone exposes them.
 *
 * <p>It does not own movement. Before it asks Baritone to path anywhere it must hold the
 * {@link MovementCoordinator}'s claim, so an autonomous run can never fight another module that
 * is also driving the player.
 */
public final class BaritoneController {

    private final BaritoneService service;
    private final MovementCoordinator coordinator;
    private final String owner;

    private int goalsIssued;
    private String lastError = "";

    public BaritoneController(BaritoneService service, MovementCoordinator coordinator, String owner) {
        this.service = service;
        this.coordinator = coordinator;
        this.owner = owner == null ? "world-intelligence" : owner;
    }

    /** True when Baritone is installed and usable. */
    public boolean available() {
        return service != null && service.available();
    }

    /** A short status line: "baritone" with the current state, or why it is unusable. */
    public String statusLine() {
        if (service == null) {
            return "baritone unavailable";
        }
        if (!service.available()) {
            String reason = service.unavailableReason();
            return reason == null || reason.isBlank() ? "baritone unavailable" : reason;
        }
        BaritoneStatus status = service.status();
        String state = status == null ? "unknown" : status.stateKey();
        return "baritone " + state + " \u00b7 " + goalsIssued + " goal(s)";
    }

    /** True when Baritone is actively pathing. */
    public boolean pathing() {
        return available() && service.status() != null && service.status().usable();
    }

    /** True when Baritone reports a pause in effect. */
    public boolean paused() {
        return available() && service.paused();
    }

    /**
     * Sends the player toward a block, if Baritone is available and this controller holds the
     * movement claim.
     *
     * @return {@code null} on success, or a short reason why it did not happen
     */
    public String goTo(int x, int y, int z) {
        if (!available()) {
            return "baritone unavailable";
        }
        if (coordinator != null && !coordinator.holds(owner)) {
            return "movement not owned by " + owner;
        }
        String error = service.goTo(BaritoneGoal.near(x, y, z, 2));
        if (error != null) {
            lastError = error;
            return error;
        }
        goalsIssued++;
        lastError = "";
        return null;
    }

    /** Cancels the current Baritone goal. Never throws; a no-op when unavailable. */
    public void cancel() {
        if (available()) {
            service.cancel();
        }
    }

    /** Pauses Baritone. Returns null on success, or a reason. */
    public String pause() {
        if (!available()) {
            return "baritone unavailable";
        }
        String error = service.pause();
        if (error != null) {
            lastError = error;
        }
        return error;
    }

    /** Resumes Baritone after a pause. Returns null on success, or a reason. */
    public String resume() {
        if (!available()) {
            return "baritone unavailable";
        }
        String error = service.resume();
        if (error != null) {
            lastError = error;
        }
        return error;
    }

    /** How many goals this controller has successfully issued. */
    public int goalsIssued() {
        return goalsIssued;
    }

    /** The last Baritone error message, or {@code ""}. */
    public String lastError() {
        return lastError;
    }
}
