package com.logvex.crystal.world.build;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

/**
 * A reversible command history for <em>local planning</em> operations: adding, moving, rotating
 * and deleting blueprint placements.
 *
 * <p>It deliberately does not touch the world. Every edit the blueprint library accepts is a
 * change to a list of planned placements, and the history stores the inverse of each change, so
 * undo/redo is exact and cannot desync the world. The stack is bounded so a long session cannot
 * grow without limit.
 */
public final class EditHistory<T> {

    private final int limit;
    private final Deque<Entry<T>> undoStack = new ArrayDeque<>();
    private final Deque<Entry<T>> redoStack = new ArrayDeque<>();
    private final List<String> labels = new ArrayList<>();

    public EditHistory(int limit) {
        this.limit = Math.max(1, limit);
    }

    /** Records an applied edit and its inverse. Clears the redo stack, as any new edit must. */
    public void record(String label, Consumer<T> redo, Consumer<T> undo) {
        undoStack.push(new Entry<>(label, redo, undo));
        while (undoStack.size() > limit) {
            undoStack.removeLast();
        }
        redoStack.clear();
        labels.add(label);
    }

    /** Undoes the most recent edit, returning its label, or null when there is nothing to undo. */
    public String undo(T target) {
        if (undoStack.isEmpty()) {
            return null;
        }
        Entry<T> entry = undoStack.pop();
        entry.undo().accept(target);
        redoStack.push(entry);
        return entry.label();
    }

    /** Redoes the most recently undone edit, returning its label, or null when none. */
    public String redo(T target) {
        if (redoStack.isEmpty()) {
            return null;
        }
        Entry<T> entry = redoStack.pop();
        entry.redo().accept(target);
        undoStack.push(entry);
        return entry.label();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /** The label of the next undo, or "" when there is none. */
    public String nextUndoLabel() {
        return undoStack.isEmpty() ? "" : undoStack.peek().label();
    }

    public int undoDepth() {
        return undoStack.size();
    }

    public int redoDepth() {
        return redoStack.size();
    }

    /** The labels of every edit still on the undo stack, most recent first. */
    public List<String> undoLabels() {
        List<String> result = new ArrayList<>();
        for (Entry<T> entry : undoStack) {
            result.add(entry.label());
        }
        return result;
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }

    private record Entry<T>(String label, Consumer<T> redo, Consumer<T> undo) {
    }
}
