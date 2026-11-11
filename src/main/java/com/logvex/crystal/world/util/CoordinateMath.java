package com.logvex.crystal.world.util;

import java.util.Locale;

/**
 * Minecraft-independent coordinate and distance arithmetic for the world utilities: the
 * coordinate HUD, the distance calculator, region/chunk maths and the waypoint HUD.
 *
 * <p>Kept pure so the rounding rules (chunk boundaries, region alignment, "which way am I
 * facing") are unit tested rather than eyeballed in game.
 */
public final class CoordinateMath {

    private CoordinateMath() {
    }

    /** The chunk coordinate that contains a block coordinate (floor division by 16). */
    public static int chunkOf(int blockCoordinate) {
        return Math.floorDiv(blockCoordinate, 16);
    }

    /** The first block coordinate of the chunk containing {@code blockCoordinate}. */
    public static int chunkOrigin(int blockCoordinate) {
        return chunkOf(blockCoordinate) * 16;
    }

    /**
     * The region coordinate (a 512-block square, Minecraft's "region file" unit) containing a
     * block coordinate. Floor division keeps the mapping correct across the origin in all
     * negative quadrants.
     */
    public static int regionOf(int blockCoordinate) {
        return Math.floorDiv(blockCoordinate, 512);
    }

    /** Euclidean distance between two points, in blocks. */
    public static double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Horizontal (XZ-plane) distance, the figure players usually mean for travel. */
    public static double horizontalDistance(double x1, double z1, double x2, double z2) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** The compass direction (yaw) from one point to another, in Minecraft's yaw convention. */
    public static double bearing(double fromX, double fromZ, double toX, double toZ) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        return Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** The 8-way compass name for a yaw in degrees, e.g. "north-east". */
    public static String compassPoint(double yaw) {
        double normalised = ((yaw % 360) + 360) % 360;
        String[] points = {"south", "south-west", "west", "north-west", "north", "north-east",
                "east", "south-east"};
        int index = (int) Math.round(normalised / 45.0) % 8;
        return points[index];
    }

    /**
     * The nether/overworld coordinate conversion used by the coordinate display: dividing by 8
     * going to the nether, multiplying by 8 coming back. Rounded toward zero so the readout
     * matches the game's own portal maths closely enough for navigation.
     */
    public static int toNether(int overworld) {
        double scaled = overworld / 8.0;
        return (int) Math.copySign(Math.round(Math.abs(scaled)), scaled);
    }

    public static int toOverworld(int nether) {
        return nether * 8;
    }

    /** A compact coordinate readout, e.g. {@code "123, 64, -45 (12, -3)"}. */
    public static String format(int x, int y, int z) {
        return String.format(Locale.ROOT, "%d, %d, %d (%d, %d)", x, y, z, chunkOf(x), chunkOf(z));
    }
}
