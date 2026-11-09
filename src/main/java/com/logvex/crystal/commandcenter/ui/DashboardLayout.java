package com.logvex.crystal.commandcenter.ui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Which dashboard widgets are visible, in what order, and how wide.
 *
 * <p>A layout is the user's dashboard customisation made concrete: an ordered list of
 * {@link DashboardWidget} with a per-widget visibility flag and a per-widget width. Every
 * mutation is validated, so a hand-edited or corrupt layout can never put the dashboard into
 * an impossible state - an unknown id is dropped, a width is clamped, and at least the
 * default order is always available.
 *
 * <p>Minecraft-independent and cheap to copy, so the screen can read a snapshot every frame
 * while the editor mutates a working copy.
 */
public final class DashboardLayout {

    /** A widget's entry in the layout: the widget, whether it is shown, and its width in columns. */
    public record Entry(DashboardWidget widget, boolean visible, int columns) {
        public Entry {
            columns = Math.max(1, Math.min(2, columns));
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    private DashboardLayout() {
    }

    /** The default layout: every widget visible, in enum order, at its natural width. */
    public static DashboardLayout defaults() {
        DashboardLayout layout = new DashboardLayout();
        for (DashboardWidget widget : DashboardWidget.values()) {
            layout.entries.add(new Entry(widget, true, widget.size()));
        }
        return layout;
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    /** The widgets currently shown, in layout order. */
    public List<DashboardWidget> visible() {
        List<DashboardWidget> result = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.visible()) {
                result.add(entry.widget());
            }
        }
        return result;
    }

    public boolean isVisible(DashboardWidget widget) {
        Entry entry = entry(widget);
        return entry != null && entry.visible();
    }

    public Entry entry(DashboardWidget widget) {
        if (widget == null) {
            return null;
        }
        for (Entry entry : entries) {
            if (entry.widget() == widget) {
                return entry;
            }
        }
        return null;
    }

    /** Shows or hides a widget, keeping its position. */
    public void setVisible(DashboardWidget widget, boolean visible) {
        replace(widget, visible, entry(widget) == null ? widget.size() : entry(widget).columns());
    }

    public void toggle(DashboardWidget widget) {
        if (widget != null) {
            setVisible(widget, !isVisible(widget));
        }
    }

    public void setColumns(DashboardWidget widget, int columns) {
        Entry existing = entry(widget);
        replace(widget, existing == null || existing.visible(), columns);
    }

    /** Moves a widget one position earlier in the order. */
    public boolean moveUp(DashboardWidget widget) {
        int index = indexOf(widget);
        if (index <= 0) {
            return false;
        }
        Entry entry = entries.remove(index);
        entries.add(index - 1, entry);
        return true;
    }

    /** Moves a widget one position later in the order. */
    public boolean moveDown(DashboardWidget widget) {
        int index = indexOf(widget);
        if (index < 0 || index >= entries.size() - 1) {
            return false;
        }
        Entry entry = entries.remove(index);
        entries.add(index + 1, entry);
        return true;
    }

    /** Restores the default order, visibility and widths. */
    public void reset() {
        entries.clear();
        entries.addAll(defaults().entries);
    }

    private void replace(DashboardWidget widget, boolean visible, int columns) {
        if (widget == null) {
            return;
        }
        int index = indexOf(widget);
        Entry entry = new Entry(widget, visible, columns);
        if (index >= 0) {
            entries.set(index, entry);
        } else {
            entries.add(entry);
        }
    }

    private int indexOf(DashboardWidget widget) {
        if (widget == null) {
            return -1;
        }
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).widget() == widget) {
                return i;
            }
        }
        return -1;
    }

    /** A deep copy, for editing without touching the live layout. */
    public DashboardLayout copy() {
        DashboardLayout copy = new DashboardLayout();
        copy.entries.addAll(entries);
        return copy;
    }

    // ------------------------------------------------------------------ codec

    /** Serializes to a compact comma-separated form: {@code id:visible:columns,...}. */
    public String encode() {
        StringBuilder sb = new StringBuilder();
        for (Entry entry : entries) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(entry.widget().id()).append(':').append(entry.visible() ? 1 : 0)
                    .append(':').append(entry.columns());
        }
        return sb.toString();
    }

    /**
     * Parses a layout string, tolerating anything malformed: unknown ids are skipped,
     * duplicate ids keep the first occurrence, missing widgets are appended in default order,
     * and an unparseable string yields the default layout.
     */
    public static DashboardLayout decode(String encoded) {
        DashboardLayout layout = new DashboardLayout();
        Set<DashboardWidget> seen = new LinkedHashSet<>();
        if (encoded != null && !encoded.isBlank()) {
            for (String token : encoded.split(",")) {
                String[] parts = token.split(":");
                DashboardWidget widget = DashboardWidget.byId(parts[0]);
                if (widget == null || !seen.add(widget)) {
                    continue;
                }
                boolean visible = parts.length < 2 || !"0".equals(parts[1]);
                int columns = widget.size();
                if (parts.length >= 3) {
                    try {
                        columns = Integer.parseInt(parts[2].trim());
                    } catch (NumberFormatException ignored) {
                        columns = widget.size();
                    }
                }
                layout.entries.add(new Entry(widget, visible, columns));
            }
        }
        // Any widget the stored string did not mention is appended in default order so a
        // newer build's widget appears rather than vanishing.
        for (DashboardWidget widget : DashboardWidget.values()) {
            if (!seen.contains(widget)) {
                layout.entries.add(new Entry(widget, true, widget.size()));
            }
        }
        return layout;
    }
}
