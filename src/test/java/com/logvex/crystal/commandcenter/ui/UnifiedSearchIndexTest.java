package com.logvex.crystal.commandcenter.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.logvex.crystal.commandcenter.ui.SearchEntry.Category;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers the unified search: ranking order, category filtering and the result cap. */
class UnifiedSearchIndexTest {

    private static UnifiedSearchIndex index() {
        UnifiedSearchIndex index = new UnifiedSearchIndex();
        index.add(SearchEntry.of(Category.MODULE, "Sprint", "Movement", "", "Sprint"));
        index.add(SearchEntry.of(Category.MODULE, "Sprint Plus", "Movement", "", "Sprint Plus"));
        index.add(SearchEntry.of(Category.SETTING, "AutoSprint", "Movement \u00B7 general", "", "Sprint"));
        index.add(SearchEntry.of(Category.COMMAND, "center", "command", "", "center"));
        index.add(SearchEntry.of(Category.WORKSPACE, "Performance", "workspace", "", "PERFORMANCE"));
        return index;
    }

    @Test
    void blankQueryReturnsNothing() {
        assertTrue(index().search("").isEmpty());
        assertTrue(index().search("   ").isEmpty());
        assertTrue(index().search(null).isEmpty());
    }

    @Test
    void exactTitleRanksFirst() {
        List<SearchEntry> results = index().search("sprint");
        assertTrue(results.size() >= 2);
        assertEquals("Sprint", results.get(0).title());
    }

    @Test
    void titlePrefixBeatsSubstring() {
        // "Sprint Plus" (prefix) outranks "AutoSprint" (substring).
        List<SearchEntry> results = index().search("sprint");
        int prefixIndex = indexOfTitle(results, "Sprint Plus");
        int substringIndex = indexOfTitle(results, "AutoSprint");
        assertTrue(prefixIndex < substringIndex);
    }

    @Test
    void matchesOnSubtitleAndTargetToo() {
        assertEquals(1, index().search("workspace").size());
        assertTrue(index().search("center").stream()
                .anyMatch(e -> e.category() == Category.COMMAND));
    }

    @Test
    void categoryFilteringWorks() {
        List<SearchEntry> modules = index().byCategory(Category.MODULE);
        assertEquals(2, modules.size());
    }

    @Test
    void resultsAreCapped() {
        UnifiedSearchIndex big = new UnifiedSearchIndex();
        for (int i = 0; i < 200; i++) {
            big.add(SearchEntry.of(Category.MODULE, "module" + i, "test", "", "module" + i));
        }
        assertTrue(big.search("module").size() <= UnifiedSearchIndex.MAX_RESULTS);
    }

    private static int indexOfTitle(List<SearchEntry> entries, String title) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).title().equals(title)) {
                return i;
            }
        }
        return -1;
    }
}
