package com.logvex.crystal.scan.world;

/**
 * The narrow port the {@link FlightController} uses to talk to the client's flight capability.
 *
 * <p>Keeping this an interface (rather than reaching into Minecraft directly) means the
 * controller's rules - detect availability, respect existing settings, detect stalls, cancel
 * safely - are unit testable with a fake, and the one real implementation lives in the
 * Minecraft adapter. The controller never assumes a "creative flight" API exists: it asks the
 * port what is available and degrades to walking when it is not.
 */
public interface FlightPort {

    /** True when the client exposes any flight capability at all (creative/spectator/E-fly). */
    boolean available();

    /** True when flight is currently switched on. */
    boolean flying();

    /** True when the player is permitted to fly right now (gamemode/abilities). */
    boolean permitted();

    /** Turns flight on or off. Returns true when the request was accepted. */
    boolean setFlying(boolean flying);

    /** Sets the desired horizontal speed in blocks per second, best effort. */
    void setSpeed(double blocksPerSecond);

    /** True when the player is in a fluid, where flight is unreliable and should pause. */
    boolean inFluid();

    /** True when the player is standing on the ground. */
    boolean onGround();

    /** Current horizontal speed in blocks per second. */
    double horizontalSpeed();

    /** A short label for the flight source, e.g. {@code "creative"} or {@code "none"}. */
    String source();
}
