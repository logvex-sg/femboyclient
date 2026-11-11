package com.logvex.crystal.gui.hud;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Pure formatting helpers for the HUD readouts.
 *
 * <p>Kept free of Minecraft types so the string a readout will show can be unit-tested
 * without a client. Anything that needs live world or player state stays in the element.
 */
public final class HudReadouts {

    private HudReadouts() {
    }

    /**
     * Formats the local wall-clock time of {@code epochMillis}.
     *
     * @param h24 true for 24-hour {@code HH:mm}, false for 12-hour {@code hh:mm AM/PM}
     */
    public static String clock(long epochMillis, boolean h24) {
        ZonedDateTime time = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault());
        if (h24) {
            return String.format("%02d:%02d", time.getHour(), time.getMinute());
        }
        int hour = time.getHour() % 12;
        if (hour == 0) {
            hour = 12;
        }
        return String.format("%d:%02d %s", hour, time.getMinute(), time.getHour() < 12 ? "AM" : "PM");
    }

    /** Compact on-screen label for the held totem count, including the offhand. */
    public static String totemLine(int total) {
        return "totems " + total;
    }

    /** Renders a yaw as a whole-degree heading, e.g. {@code "yaw 137"}. */
    public static String yawLine(float yaw) {
        return "yaw " + Math.round(yaw);
    }
}
