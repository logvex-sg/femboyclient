package com.logvex.crystal.gui.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

/** Unit tests for the pure HUD readout formatters. */
class HudReadoutsTest {

    @Test
    void clock24HourIsZeroPaddedHhMm() {
        long epoch = Instant.parse("2024-03-05T08:07:00Z").toEpochMilli();
        ZonedDateTime local = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault());
        assertEquals(String.format("%02d:%02d", local.getHour(), local.getMinute()),
                HudReadouts.clock(epoch, true));
    }

    @Test
    void clock12HourUsesTwelveHourClockWithMeridiem() {
        long epoch = Instant.parse("2024-03-05T08:07:00Z").toEpochMilli();
        ZonedDateTime local = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault());
        int expectedHour = local.getHour() % 12;
        if (expectedHour == 0) {
            expectedHour = 12;
        }
        String meridiem = local.getHour() < 12 ? "AM" : "PM";
        assertEquals(String.format("%d:%02d %s", expectedHour, local.getMinute(), meridiem),
                HudReadouts.clock(epoch, false));
    }

    @Test
    void clock12HourNeverShowsHourZero() {
        for (int hour = 0; hour < 24; hour++) {
            long epoch = Instant.parse("2024-01-01T00:00:00Z").toEpochMilli() + hour * 3_600_000L;
            String rendered = HudReadouts.clock(epoch, false);
            int shown = Integer.parseInt(rendered.substring(0, rendered.indexOf(':')));
            assertTrue(shown >= 1 && shown <= 12, "12h hour must be 1..12 but was " + shown);
        }
    }

    @Test
    void totemLineIsPluralisedCount() {
        assertEquals("totems 0", HudReadouts.totemLine(0));
        assertEquals("totems 64", HudReadouts.totemLine(64));
    }

    @Test
    void yawLineRoundsToWholeDegrees() {
        assertEquals("yaw 0", HudReadouts.yawLine(0f));
        assertEquals("yaw 137", HudReadouts.yawLine(136.6f));
        assertEquals("yaw -90", HudReadouts.yawLine(-90f));
    }

    @Test
    void serverLabelStripsPortAndMarksSingleplayer() {
        assertEquals("play.example.com", HudReadouts.serverLabel("play.example.com:25565"));
        assertEquals("localhost", HudReadouts.serverLabel("localhost"));
        assertEquals("singleplayer", HudReadouts.serverLabel(null));
        assertEquals("singleplayer", HudReadouts.serverLabel("  "));
    }

    @Test
    void inventoryLineCountsFreeSlotsAndClamps() {
        assertEquals("inv 24/36  free 12", HudReadouts.inventoryLine(24, 36));
        assertEquals("inv 36/36  free 0", HudReadouts.inventoryLine(40, 36));
        assertEquals("inv 0/36  free 36", HudReadouts.inventoryLine(-5, 36));
    }
}
