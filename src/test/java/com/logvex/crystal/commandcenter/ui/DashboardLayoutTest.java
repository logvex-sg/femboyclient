package com.logvex.crystal.commandcenter.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Covers the dashboard layout: visibility, ordering, widths and the tolerant codec. */
class DashboardLayoutTest {

    @Test
    void defaultsShowEveryWidgetInEnumOrder() {
        DashboardLayout layout = DashboardLayout.defaults();
        assertEquals(DashboardWidget.values().length, layout.visible().size());
        assertEquals(DashboardWidget.values()[0], layout.visible().get(0));
        for (DashboardWidget widget : DashboardWidget.values()) {
            assertTrue(layout.isVisible(widget));
        }
    }

    @Test
    void togglingVisibilityKeepsOrder() {
        DashboardLayout layout = DashboardLayout.defaults();
        layout.toggle(DashboardWidget.PERFORMANCE);
        assertFalse(layout.isVisible(DashboardWidget.PERFORMANCE));
        assertEquals(DashboardWidget.values().length - 1, layout.visible().size());
        layout.toggle(DashboardWidget.PERFORMANCE);
        assertTrue(layout.isVisible(DashboardWidget.PERFORMANCE));
    }

    @Test
    void movingReordersWithinBounds() {
        DashboardLayout layout = DashboardLayout.defaults();
        DashboardWidget first = layout.visible().get(0);
        assertFalse(layout.moveUp(first));
        assertTrue(layout.moveDown(first));
        assertNotEquals(first, layout.visible().get(0));
    }

    @Test
    void encodeDecodeRoundTrips() {
        DashboardLayout layout = DashboardLayout.defaults();
        layout.toggle(DashboardWidget.AI);
        layout.setColumns(DashboardWidget.MODULES, 2);
        String encoded = layout.encode();
        DashboardLayout decoded = DashboardLayout.decode(encoded);
        assertEquals(encoded, decoded.encode());
        assertFalse(decoded.isVisible(DashboardWidget.AI));
        assertEquals(2, decoded.entry(DashboardWidget.MODULES).columns());
    }

    @Test
    void decodeToleratesMalformedInput() {
        // Unknown ids are skipped, duplicates keep the first, garbage yields defaults.
        DashboardLayout layout = DashboardLayout.decode("nope:1:1,health:0:1,health:1:1");
        assertFalse(layout.isVisible(DashboardWidget.HEALTH));
        assertEquals(DashboardWidget.values().length, layout.entries().size());

        DashboardLayout garbage = DashboardLayout.decode("!!!not-a-layout!!!");
        assertEquals(DashboardWidget.values().length, garbage.entries().size());
    }

    @Test
    void decodeAppendsMissingWidgetsSoNewOnesAppear() {
        DashboardLayout layout = DashboardLayout.decode("health:1:1");
        assertTrue(layout.entries().size() >= DashboardWidget.values().length);
        assertEquals(DashboardWidget.values().length, layout.entries().size());
    }

    @Test
    void columnsAreClamped() {
        DashboardLayout layout = DashboardLayout.defaults();
        layout.setColumns(DashboardWidget.HEALTH, 9);
        assertEquals(2, layout.entry(DashboardWidget.HEALTH).columns());
        layout.setColumns(DashboardWidget.HEALTH, 0);
        assertEquals(1, layout.entry(DashboardWidget.HEALTH).columns());
    }
}
