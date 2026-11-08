package com.logvex.crystal.gui.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.logvex.crystal.scan.world.DiscoveryDatabase;
import com.logvex.crystal.scan.world.StructureCategory;
import com.logvex.crystal.scan.world.WorldQuery;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests the dashboard's interaction state: tab navigation, search, filter and sort cycling, list
 * selection and paging, map zoom, the mutually exclusive text buffers and the answer log. This is
 * the logic behind {@code WorldIntelligenceScreen}, tested without a running game.
 */
class IntelligenceDashboardStateTest {

    @Test
    void startsOnOverviewWithNoSelection() {
        IntelligenceDashboardState state = new IntelligenceDashboardState();
        assertEquals(IntelligenceDashboardState.Tab.OVERVIEW, state.tab());
        assertEquals(-1, state.selectedIndex());
        assertNull(state.filter());
        assertEquals(DiscoveryDatabase.SortOrder.RECENT, state.sort());
    }

    @Test
    void tabsCycleForwardsAndBackwards() {
        IntelligenceDashboardState state = new IntelligenceDashboardState();
        assertEquals(IntelligenceDashboardState.Tab.MAP, state.nextTab());
        assertEquals(IntelligenceDashboardState.Tab.DISCOVERIES, state.nextTab());
        assertEquals(IntelligenceDashboardState.Tab.MAP, state.previousTab());
        assertEquals(IntelligenceDashboardState.Tab.OVERVIEW, state.previousTab());
        // Wraps around both ends.
        assertEquals(IntelligenceDashboardState.Tab.DATA, state.previousTab());
        state.selectTab(null);
        assertEquals(IntelligenceDashboardState.Tab.DATA, state.tab());
    }

    @Test
    void searchAppendsBackspacesAndClearsSelection() {
        IntelligenceDashboardState state = new IntelligenceDashboardState();
        state.selectRow(4);
        state.appendSearch("sto");
        assertEquals("sto", state.search());
        assertEquals(-1, state.selectedIndex(), "typing a new search clears the stale selection");
        state.backspaceSearch();
        assertEquals("st", state.search());
        state.clearSearch();
        assertEquals("", state.search());
        // Backspacing an empty search is a no-op, never an exception.
        state.backspaceSearch();
        assertEquals("", state.search());
    }

    @Test
    void filterCyclesThroughEveryCategoryThenBackToAll() {
        IntelligenceDashboardState state = new IntelligenceDashboardState();
        assertNull(state.filter());
        for (StructureCategory category : StructureCategory.values()) {
            assertEquals(category, state.cycleFilter());
        }
        assertNull(state.cycleFilter(), "cycling past the last category returns to 'all'");
    }

    @Test
    void filterAndSortCyclingClearTheSelection() {
        IntelligenceDashboardState state = new IntelligenceDashboardState();
        state.selectRow(3);
        state.cycleFilter();
        assertEquals(-1, state.selectedIndex());
        state.selectRow(3);
        state.cycleSort();
        assertEquals(-1, state.selectedIndex());
    }

    @Test
    void sortCyclesThroughEveryOrder() {
        IntelligenceDashboardState state = new IntelligenceDashboardState();
        DiscoveryDatabase.SortOrder[] values = DiscoveryDatabase.SortOrder.values();
        for (int i = 0; i < values.length; i++) {
            state.cycleSort();
        }
        assertEquals(values[0], state.sort(), "a full cycle returns to the first order");
    }

    @Test
    void listScrollNeverGoesNegative() {
        IntelligenceDashboardState state = new IntelligenceDashboardState();
        state.scrollList(5);
        assertEquals(5, state.listScroll());
        state.scrollList(-100);
        assertEquals(0, state.listScroll());
    }

    @Test
    void mapZoomClampsToItsRange() {
        IntelligenceDashboardState state = new IntelligenceDashboardState();
        assertEquals(4, state.mapZoom());
        for (int i = 0; i < 50; i++) {
            state.zoom(1);
        }
        assertEquals(16, state.mapZoom());
        for (int i = 0; i < 50; i++) {
            state.zoom(-1);
        }
        assertEquals(1, state.mapZoom());
    }

    @Test
    void questionAndAnnotationAreMutuallyExclusive() {
        IntelligenceDashboardState state = new IntelligenceDashboardState();
        state.beginAnnotate(2, "old note");
        assertEquals(2, state.annotateTargetIndex());
        assertEquals("old note", state.annotateBuffer());

        state.focusQuestion();
        assertTrue(state.questionFocused());
        assertEquals(-1, state.annotateTargetIndex(), "focusing the question box cancels annotation");
        assertEquals("", state.annotateBuffer());

        state.beginAnnotate(3, "");
        assertFalse(state.questionFocused(), "annotating blurs the question box");
    }

    @Test
    void takeQuestionTrimsAndClears() {
        IntelligenceDashboardState state = new IntelligenceDashboardState();
        state.appendQuestion("  where are the chests?  ");
        assertEquals("where are the chests?", state.takeQuestion());
        assertEquals("", state.question());
        assertEquals("", state.takeQuestion(), "an empty box yields an empty question");
    }

    @Test
    void takeAnnotationReturnsNullWhenNotAnnotating() {
        IntelligenceDashboardState state = new IntelligenceDashboardState();
        assertNull(state.takeAnnotation());
        state.beginAnnotate(1, "seed");
        state.appendAnnotation("ling");
        assertEquals("seedling", state.takeAnnotation());
        assertEquals(-1, state.annotateTargetIndex());
        assertNull(state.takeAnnotation());
    }

    @Test
    void backspaceStopsAtEmptyBuffers() {
        IntelligenceDashboardState state = new IntelligenceDashboardState();
        state.backspaceQuestion();
        state.backspaceAnnotation();
        assertEquals("", state.question());
        assertEquals("", state.annotateBuffer());
    }

    @Test
    void blurEndsEditingAndReportsWhetherItDid() {
        IntelligenceDashboardState state = new IntelligenceDashboardState();
        assertFalse(state.blur());
        state.focusQuestion();
        assertTrue(state.blur());
        assertFalse(state.questionFocused());
        state.beginAnnotate(0, "x");
        assertTrue(state.blur());
        assertEquals(-1, state.annotateTargetIndex());
    }

    @Test
    void answersAccumulateAndIgnoreNull() {
        IntelligenceDashboardState state = new IntelligenceDashboardState();
        state.addAnswer(null);
        assertTrue(state.answers().isEmpty());
        state.addAnswer(new WorldQuery.Answer("q1", "count", "one", List.of("id"), false));
        state.addAnswer(new WorldQuery.Answer("q2", "count", "two", List.of(), true));
        assertEquals(2, state.answers().size());
        assertEquals("q1", state.answers().get(0).question());
        state.clearAnswers();
        assertTrue(state.answers().isEmpty());
    }

    @Test
    void selectRowRejectsNegativeIndices() {
        IntelligenceDashboardState state = new IntelligenceDashboardState();
        state.selectRow(5);
        assertEquals(5, state.selectedIndex());
        state.selectRow(-3);
        assertEquals(-1, state.selectedIndex());
    }
}
