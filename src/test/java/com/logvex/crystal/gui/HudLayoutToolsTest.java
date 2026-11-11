package com.logvex.crystal.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Alignment guides and undo/redo for the HUD layout editor. */
class HudLayoutToolsTest {

    // ------------------------------------------------------------ HudGuides

    @Test
    void findsAVerticalGuideOnMatchingLeftEdges() {
        Map<String, HudSnap.Rect> others = new LinkedHashMap<>();
        others.put("a", new HudSnap.Rect(10, 10, 50, 20));
        HudGuides.Result result = HudGuides.find(new HudSnap.Rect(12, 100, 50, 20), others, 5);
        assertTrue(result.vertical().contains(10));
        assertNotNull(result.snapX());
        assertEquals(10, result.snapX());
    }

    @Test
    void snapsTheMovingRectOntoTheGuide() {
        Map<String, HudSnap.Rect> others = new LinkedHashMap<>();
        others.put("a", new HudSnap.Rect(100, 10, 50, 20));
        // Moving rect spans 103..143 (centre 123); the other centre is 125, the closest
        // match, so the rect shifts by +2 so the centres line up.
        HudGuides.Result result = HudGuides.find(new HudSnap.Rect(103, 200, 40, 20), others, 5);
        assertEquals(105, result.snapX());
        assertTrue(result.vertical().contains(125));
    }

    @Test
    void reportsNoGuideWhenNothingIsClose() {
        Map<String, HudSnap.Rect> others = new LinkedHashMap<>();
        others.put("a", new HudSnap.Rect(10, 10, 50, 20));
        HudGuides.Result result = HudGuides.find(new HudSnap.Rect(500, 500, 40, 20), others, 5);
        assertTrue(result.vertical().isEmpty());
        assertTrue(result.horizontal().isEmpty());
        assertNull(result.snapX());
        assertNull(result.snapY());
    }

    @Test
    void findsAHorizontalGuideOnMatchingTopEdges() {
        Map<String, HudSnap.Rect> others = new LinkedHashMap<>();
        others.put("a", new HudSnap.Rect(0, 40, 50, 20));
        HudGuides.Result result = HudGuides.find(new HudSnap.Rect(200, 42, 40, 20), others, 5);
        assertTrue(result.horizontal().contains(40));
        assertEquals(40, result.snapY());
    }

    @Test
    void centreAlignmentAlsoCountsAsAGuide() {
        Map<String, HudSnap.Rect> others = new LinkedHashMap<>();
        // Other spans x 0..100, centre at 50.
        others.put("a", new HudSnap.Rect(0, 0, 100, 20));
        // Moving spans x 30..70, centre at 50.
        HudGuides.Result result = HudGuides.find(new HudSnap.Rect(30, 200, 40, 20), others, 3);
        assertTrue(result.vertical().contains(50));
    }

    // ------------------------------------------------------------ HudLayoutHistory

    @Test
    void undoRestoresThePreviousLayout() {
        HudLayout layout = new HudLayout();
        layout.entry("a").x = 10;
        HudLayoutHistory history = new HudLayoutHistory();
        history.push(layout);
        layout.entry("a").x = 99;
        assertTrue(history.undo(layout));
        assertEquals(10, layout.entry("a").x);
    }

    @Test
    void redoReappliesAnUndoneEdit() {
        HudLayout layout = new HudLayout();
        layout.entry("a").x = 10;
        HudLayoutHistory history = new HudLayoutHistory();
        history.push(layout);
        layout.entry("a").x = 99;
        history.undo(layout);
        assertTrue(history.redo(layout));
        assertEquals(99, layout.entry("a").x);
    }

    @Test
    void aNewEditClearsTheRedoStack() {
        HudLayout layout = new HudLayout();
        HudLayoutHistory history = new HudLayoutHistory();
        history.push(layout);
        layout.entry("a").x = 1;
        history.undo(layout);
        assertTrue(history.canRedo());
        history.push(layout);
        assertFalse(history.canRedo());
    }

    @Test
    void undoOnAnEmptyStackIsANoOp() {
        HudLayout layout = new HudLayout();
        HudLayoutHistory history = new HudLayoutHistory();
        assertFalse(history.undo(layout));
        assertFalse(history.redo(layout));
    }

    @Test
    void snapshotsAreIndependentOfLaterEdits() {
        HudLayout layout = new HudLayout();
        layout.entry("a").x = 1;
        layout.entry("a").background = true;
        HudLayoutHistory history = new HudLayoutHistory();
        history.push(layout);
        layout.entry("a").x = 500;
        layout.entry("a").background = false;
        history.undo(layout);
        assertEquals(1, layout.entry("a").x);
        assertTrue(layout.entry("a").background);
    }

    @Test
    void resetClearsLayerAndResizeOverrides() {
        HudLayout.Entry entry = new HudLayout.Entry();
        entry.layer = 3;
        entry.width = 200;
        entry.height = 40;
        HudSnap.reset(entry);
        assertEquals(0, entry.layer);
        assertEquals(0, entry.width);
        assertEquals(0, entry.height);
    }
}
