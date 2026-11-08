package com.logvex.crystal.scan.world;

/**
 * The flight controller: engages, maintains and safely releases the client's flight capability
 * for the scanner.
 *
 * <p>Design rules (all verified against a fake {@link FlightPort} in tests):
 * <ul>
 *   <li><b>Detect availability</b> - it never assumes flight exists; an unavailable or
 *       unpermitted port is reported, not forced.</li>
 *   <li><b>Respect existing settings</b> - if the player is already flying, it adopts that state
 *       and does not overwrite their speed; it only records that it did <em>not</em> start it, so
 *       {@link #release} will not switch off a flight the user set up.</li>
 *   <li><b>Detect movement failures</b> - while a target is far away, if the horizontal speed
 *       stays near zero for {@value #STALL_TICKS} consecutive ticks it declares a stall, releases
 *       flight and reports it, rather than flying forever into a wall.</li>
 *   <li><b>Handle collisions and fluids</b> - in a fluid it pauses (keeps flight on but reports
 *       {@link FlightPhase#BLOCKED}) instead of fighting the water.</li>
 *   <li><b>Safe cancellation</b> - {@link #release} only turns flight off when the controller
 *       engaged it.</li>
 * </ul>
 *
 * <p>Minecraft-free: the port abstracts every game touch.
 */
public final class FlightController {

    /** Consecutive near-zero-speed ticks before a stall is declared. */
    public static final int STALL_TICKS = 40;
    /** Horizontal speed (blocks/second) below which a tick counts toward a stall. */
    private static final double STALL_SPEED = 0.35;

    /** The controller's current phase, for the HUD/dashboard. */
    public enum FlightPhase {
        INACTIVE("inactive"),
        UNAVAILABLE("unavailable"),
        ENGAGED("engaged"),
        ADOPTED("adopted"),
        BLOCKED("blocked"),
        STALLED("stalled");

        private final String label;

        FlightPhase(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** The outcome of an engage/release call. */
    public record FlightResult(boolean ok, FlightPhase phase, String message) {
    }

    private FlightPhase phase = FlightPhase.INACTIVE;
    private boolean engagedByUs;
    private int stallTicks;
    private String lastMessage = "";

    public FlightPhase phase() {
        return phase;
    }

    /** True when this controller switched flight on and is therefore responsible for it. */
    public boolean engagedByUs() {
        return engagedByUs;
    }

    public int stallTicks() {
        return stallTicks;
    }

    public String lastMessage() {
        return lastMessage;
    }

    /** A short status line for the dashboard, e.g. {@code "creative · engaged"}. */
    public String statusLine(FlightPort port) {
        if (port == null) {
            return "no flight";
        }
        return port.source() + " \u00b7 " + phase.label()
                + (port.available() ? "" : " (unavailable)");
    }

    /**
     * Engages flight at {@code speed}. Adopts an existing flight instead of overriding it, and
     * reports why when flight cannot be engaged so the caller can fall back to walking.
     */
    public FlightResult engage(FlightPort port, double speed) {
        stallTicks = 0;
        if (port == null || !port.available()) {
            phase = FlightPhase.UNAVAILABLE;
            lastMessage = "flight unavailable; walking";
            engagedByUs = false;
            return new FlightResult(false, phase, lastMessage);
        }
        if (!port.permitted()) {
            phase = FlightPhase.UNAVAILABLE;
            lastMessage = "flight not permitted here; walking";
            engagedByUs = false;
            return new FlightResult(false, phase, lastMessage);
        }
        if (port.flying()) {
            // The player already flies: adopt it, do not touch their speed.
            phase = FlightPhase.ADOPTED;
            engagedByUs = false;
            lastMessage = "adopted existing flight";
            return new FlightResult(true, phase, lastMessage);
        }
        boolean accepted = port.setFlying(true);
        if (!accepted) {
            phase = FlightPhase.UNAVAILABLE;
            lastMessage = "flight request refused; walking";
            engagedByUs = false;
            return new FlightResult(false, phase, lastMessage);
        }
        port.setSpeed(speed);
        phase = FlightPhase.ENGAGED;
        engagedByUs = true;
        lastMessage = "flight engaged";
        return new FlightResult(true, phase, lastMessage);
    }

    /**
     * Called once per tick while exploring toward {@code (targetX,targetZ)} from
     * {@code (currentX,currentZ)}. Returns true when flight is healthy, false when it has stalled
     * or been blocked. A fluid pauses rather than stalls; a near-zero speed for
     * {@value #STALL_TICKS} ticks while the target is far declares a stall and releases flight.
     */
    public boolean maintain(FlightPort port, int targetX, int targetZ, int currentX, int currentZ) {
        if (port == null) {
            return false;
        }
        if (phase == FlightPhase.INACTIVE || phase == FlightPhase.UNAVAILABLE) {
            return false;
        }
        if (port.inFluid()) {
            phase = FlightPhase.BLOCKED;
            lastMessage = "in fluid; flight paused";
            stallTicks = 0;
            return false;
        }
        double dx = targetX - currentX;
        double dz = targetZ - currentZ;
        boolean far = dx * dx + dz * dz > 4;
        if (far && port.horizontalSpeed() < STALL_SPEED && !port.onGround()) {
            stallTicks++;
            if (stallTicks >= STALL_TICKS) {
                phase = FlightPhase.STALLED;
                lastMessage = "movement stalled";
                release(port);
                phase = FlightPhase.STALLED;
                return false;
            }
        } else {
            stallTicks = 0;
            if (phase == FlightPhase.BLOCKED || phase == FlightPhase.ADOPTED) {
                phase = engagedByUs ? FlightPhase.ENGAGED : FlightPhase.ADOPTED;
            }
        }
        return true;
    }

    /**
     * Safely releases flight. Only turns flight off when this controller engaged it; a flight the
     * user already had is left exactly as it was. Never throws.
     */
    public FlightResult release(FlightPort port) {
        boolean ours = engagedByUs;
        engagedByUs = false;
        stallTicks = 0;
        if (port != null && ours && port.flying()) {
            port.setFlying(false);
        }
        FlightPhase previous = phase;
        phase = FlightPhase.INACTIVE;
        lastMessage = ours ? "flight released" : "flight left as-is";
        return new FlightResult(true, phase, lastMessage);
    }

    /** Resets the controller to its initial state without touching the port. */
    public void reset() {
        phase = FlightPhase.INACTIVE;
        engagedByUs = false;
        stallTicks = 0;
        lastMessage = "";
    }
}
