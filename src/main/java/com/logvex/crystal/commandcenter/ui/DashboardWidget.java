package com.logvex.crystal.commandcenter.ui;

import java.util.Locale;

/**
 * A single dashboard card the user can show, hide and arrange.
 *
 * <p>Each widget has a stable id (persisted), a display title, the status section it renders
 * and a size hint. The dashboard reads the {@link DashboardLayout} to decide which widgets to
 * draw and in what order, so a customisation change is a layout change, never a code change.
 *
 * <p>Minecraft-independent: the enum only names intent; the screen owns the drawing.
 */
public enum DashboardWidget {

    CLIENT("client", "Client", "Status", 2),
    HEALTH("health", "System health", "health", 1),
    PERFORMANCE("performance", "Performance", "performance", 2),
    AI("ai", "AI assistant", "ai", 1),
    BARITONE("baritone", "Baritone", "baritone", 1),
    MODULES("modules", "Modules", "modules", 1),
    PROFILES("profiles", "Profiles", "profiles", 1),
    EVENTS("events", "Events", "events", 1);

    private final String id;
    private final String title;
    private final String sectionId;
    private final int size; // 1 = half width, 2 = full width

    DashboardWidget(String id, String title, String sectionId, int size) {
        this.id = id;
        this.title = title;
        this.sectionId = sectionId;
        this.size = size;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    /** The status section this widget renders, or empty when it renders a synthetic view. */
    public String sectionId() {
        return sectionId;
    }

    /** 1 for a half-width card, 2 for a full-width card. */
    public int size() {
        return size;
    }

    public static DashboardWidget byId(String id) {
        if (id == null) {
            return null;
        }
        String needle = id.trim().toLowerCase(Locale.ROOT);
        for (DashboardWidget widget : values()) {
            if (widget.id.equals(needle)) {
                return widget;
            }
        }
        return null;
    }
}
