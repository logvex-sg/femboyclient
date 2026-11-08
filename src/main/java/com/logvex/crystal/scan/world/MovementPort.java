package com.logvex.crystal.scan.world;

/**
 * The port the scanner uses to move the player between route waypoints.
 *
 * <p>The scanner never drives Minecraft's input or interaction managers itself: it asks the port
 * to head toward a waypoint and to stop. The Minecraft adapter can back this with Baritone when
 * it is present, and does nothing (reporting {@code unavailable}) when it is not, so exploration
 * degrades gracefully to "scan around the player" rather than pretending to navigate.
 */
public interface MovementPort {

    /** True when this port can actually move the player (e.g. Baritone is present). */
    boolean available();

    /** Requests that the player walk/fly toward a block position. A no-op when unavailable. */
    void goTo(int x, int y, int z);

    /** Stops any movement this port started. Must not disturb movement it did not start. */
    void stop();

    /** A short label for the movement source, e.g. {@code "baritone"} or {@code "none"}. */
    String source();
}
