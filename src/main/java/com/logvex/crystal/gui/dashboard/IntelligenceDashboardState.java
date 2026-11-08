package com.logvex.crystal.gui.dashboard;

import com.logvex.crystal.scan.world.DiscoveryDatabase;
import com.logvex.crystal.scan.world.StructureCategory;
import com.logvex.crystal.scan.world.WorldQuery;
import java.util.ArrayList;
import java.util.List;

/**
 * The world-intelligence dashboard's interaction state, kept deliberately free of any Minecraft
 * type so the whole interaction model can be unit tested without a running game.
 *
 * <p>{@code WorldIntelligenceScreen} owns one of these and delegates every interaction to it:
 * tab selection, the search box, the category filter and sort cycling, row selection and paging,
 * the map zoom, the AI question box and the annotation buffer. The screen only turns this state
 * into pixels and turns clicks and keystrokes back into method calls, so a bug in the interaction
 * logic is a bug in this class and is caught by its tests.
 *
 * <p>Editing model: at most one text field is "focused" at a time. Focusing the question box
 * clears the annotation target and vice versa, so a keystroke can never land in two buffers.
 */
public final class IntelligenceDashboardState {

    /** The five workspaces of the dashboard. */
    public enum Tab {
        OVERVIEW,
        MAP,
        DISCOVERIES,
        AI,
        DATA
    }

    private Tab tab = Tab.OVERVIEW;

    private String search = "";
    private StructureCategory filter;
    private DiscoveryDatabase.SortOrder sort = DiscoveryDatabase.SortOrder.RECENT;
    private int selectedIndex = -1;
    private int listScroll;

    private int mapZoom = 4;

    private boolean questionFocused;
    private String question = "";
    private int annotateTargetIndex = -1;
    private String annotateBuffer = "";

    private final List<WorldQuery.Answer> answers = new ArrayList<>();

    // ------------------------------------------------------------------ tabs

    public Tab tab() {
        return tab;
    }

    public void selectTab(Tab next) {
        if (next != null) {
            this.tab = next;
        }
    }

    /** Cycles to the next workspace; used by keyboard navigation and the tab strip. */
    public Tab nextTab() {
        Tab[] values = Tab.values();
        tab = values[(tab.ordinal() + 1) % values.length];
        return tab;
    }

    public Tab previousTab() {
        Tab[] values = Tab.values();
        tab = values[(tab.ordinal() - 1 + values.length) % values.length];
        return tab;
    }

    // ------------------------------------------------------------------ search & filter

    public String search() {
        return search;
    }

    public void appendSearch(String text) {
        if (text != null && !text.isEmpty()) {
            search += text;
            selectedIndex = -1;
            listScroll = 0;
        }
    }

    public void backspaceSearch() {
        if (!search.isEmpty()) {
            search = search.substring(0, search.length() - 1);
        }
    }

    public void clearSearch() {
        search = "";
    }

    /** The active category filter, or {@code null} for "all categories". */
    public StructureCategory filter() {
        return filter;
    }

    /** Cycles all -> each category -> all. */
    public StructureCategory cycleFilter() {
        StructureCategory[] values = StructureCategory.values();
        if (filter == null) {
            filter = values[0];
        } else {
            int next = filter.ordinal() + 1;
            filter = next >= values.length ? null : values[next];
        }
        selectedIndex = -1;
        listScroll = 0;
        return filter;
    }

    public DiscoveryDatabase.SortOrder sort() {
        return sort;
    }

    public DiscoveryDatabase.SortOrder cycleSort() {
        DiscoveryDatabase.SortOrder[] values = DiscoveryDatabase.SortOrder.values();
        sort = values[(sort.ordinal() + 1) % values.length];
        // Re-ordering the list invalidates the row index, so drop the selection.
        selectedIndex = -1;
        return sort;
    }

    // ------------------------------------------------------------------ list selection

    public int selectedIndex() {
        return selectedIndex;
    }

    public void selectRow(int index) {
        selectedIndex = index < 0 ? -1 : index;
    }

    public void clearSelection() {
        selectedIndex = -1;
    }

    public int listScroll() {
        return listScroll;
    }

    /** Scrolls the discoveries list by {@code rows}, never below zero. */
    public void scrollList(int rows) {
        listScroll = Math.max(0, listScroll + rows);
    }

    // ------------------------------------------------------------------ map

    public int mapZoom() {
        return mapZoom;
    }

    /** Zooms the map by {@code delta} pixels per chunk, clamped to a sane range. */
    public int zoom(int delta) {
        mapZoom = Math.max(1, Math.min(16, mapZoom + delta));
        return mapZoom;
    }

    // ------------------------------------------------------------------ question box

    public boolean questionFocused() {
        return questionFocused;
    }

    public String question() {
        return question;
    }

    /** Focuses the AI question box, ending any annotation in progress. */
    public void focusQuestion() {
        questionFocused = true;
        annotateTargetIndex = -1;
        annotateBuffer = "";
    }

    public void appendQuestion(String text) {
        if (text != null && !text.isEmpty()) {
            question += text;
        }
    }

    public void backspaceQuestion() {
        if (!question.isEmpty()) {
            question = question.substring(0, question.length() - 1);
        }
    }

    /** Returns the trimmed question and clears the box, or {@code ""} when the box is empty. */
    public String takeQuestion() {
        String asked = question.trim();
        question = "";
        return asked;
    }

    // ------------------------------------------------------------------ annotation

    public int annotateTargetIndex() {
        return annotateTargetIndex;
    }

    public String annotateBuffer() {
        return annotateBuffer;
    }

    /** Starts annotating the row at {@code index}, seeding the buffer with the current note. */
    public void beginAnnotate(int index, String currentNote) {
        annotateTargetIndex = index;
        annotateBuffer = currentNote == null ? "" : currentNote;
        questionFocused = false;
    }

    public void appendAnnotation(String text) {
        if (text != null && !text.isEmpty()) {
            annotateBuffer += text;
        }
    }

    public void backspaceAnnotation() {
        if (!annotateBuffer.isEmpty()) {
            annotateBuffer = annotateBuffer.substring(0, annotateBuffer.length() - 1);
        }
    }

    /** Ends annotation and returns the buffer, or {@code null} when no annotation was in progress. */
    public String takeAnnotation() {
        if (annotateTargetIndex < 0) {
            return null;
        }
        String note = annotateBuffer;
        annotateTargetIndex = -1;
        annotateBuffer = "";
        return note;
    }

    public void cancelAnnotation() {
        annotateTargetIndex = -1;
        annotateBuffer = "";
    }

    // ------------------------------------------------------------------ answers

    public void addAnswer(WorldQuery.Answer answer) {
        if (answer != null) {
            answers.add(answer);
        }
    }

    public List<WorldQuery.Answer> answers() {
        return List.copyOf(answers);
    }

    public void clearAnswers() {
        answers.clear();
    }

    // ------------------------------------------------------------------ blur

    /** Ends any in-progress text editing; returns true when something was blurred. */
    public boolean blur() {
        if (questionFocused || annotateTargetIndex >= 0) {
            questionFocused = false;
            annotateTargetIndex = -1;
            annotateBuffer = "";
            return true;
        }
        return false;
    }
}
