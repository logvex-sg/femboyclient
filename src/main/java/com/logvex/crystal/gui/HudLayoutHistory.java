package com.logvex.crystal.gui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A bounded undo/redo stack for HUD layout edits. Each entry is a full snapshot of the
 * per-element placement map, which is small (a few dozen entries) and cheap to copy, so a
 * snapshot per edit keeps the implementation simple and correct.
 *
 * <p>Snapshots are plain data ({@link HudLayout.Entry} copies), so the editor can restore a
 * layout without touching the live entry objects it already handed to the renderer.
 */
public final class HudLayoutHistory {

    private static final int LIMIT = 50;

    private final Deque<Map<String, HudLayout.Entry>> undo = new ArrayDeque<>();
    private final Deque<Map<String, HudLayout.Entry>> redo = new ArrayDeque<>();

    /** Records the current layout as an undo point and clears the redo stack. */
    public void push(HudLayout layout) {
        undo.addLast(snapshot(layout));
        while (undo.size() > LIMIT) {
            undo.removeFirst();
        }
        redo.clear();
    }

    /** Restores the previous snapshot into {@code layout}, if any. */
    public boolean undo(HudLayout layout) {
        if (undo.isEmpty()) {
            return false;
        }
        redo.addLast(snapshot(layout));
        restore(layout, undo.removeLast());
        return true;
    }

    /** Re-applies the last undone snapshot into {@code layout}, if any. */
    public boolean redo(HudLayout layout) {
        if (redo.isEmpty()) {
            return false;
        }
        undo.addLast(snapshot(layout));
        restore(layout, redo.removeLast());
        return true;
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public void clear() {
        undo.clear();
        redo.clear();
    }

    public int undoDepth() {
        return undo.size();
    }

    private static Map<String, HudLayout.Entry> snapshot(HudLayout layout) {
        Map<String, HudLayout.Entry> copy = new LinkedHashMap<>();
        for (Map.Entry<String, HudLayout.Entry> e : layout.all().entrySet()) {
            copy.put(e.getKey(), e.getValue().copy());
        }
        return copy;
    }

    private static void restore(HudLayout layout, Map<String, HudLayout.Entry> snapshot) {
        layout.all().clear();
        for (Map.Entry<String, HudLayout.Entry> e : snapshot.entrySet()) {
            layout.all().put(e.getKey(), e.getValue().copy());
        }
    }
}
