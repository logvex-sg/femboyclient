package com.logvex.crystal.commandcenter.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The unified search over everything the Command Center knows about.
 *
 * <p>It indexes modules, their settings, the command surface, saved workspaces and profiles and
 * the registered actions into one flat list of {@link SearchEntry}, then matches a query
 * against it. Ranking is deliberately simple and predictable: an exact title match wins, then
 * a title prefix, then a title substring, then a subtitle/haystack match, with the category's
 * natural order breaking ties. That makes the result list stable - the same query always
 * returns the same order - which matters when the user is typing fast during combat.
 *
 * <p>Minecraft-independent: the index is built from plain data by the caller, so the whole
 * matcher is unit testable.
 */
public final class UnifiedSearchIndex {

    private final List<SearchEntry> entries = new ArrayList<>();

    /** The maximum number of results returned for one query, so a broad query cannot flood the UI. */
    public static final int MAX_RESULTS = 40;

    public void add(SearchEntry entry) {
        if (entry != null) {
            entries.add(entry);
        }
    }

    public void addAll(List<SearchEntry> more) {
        if (more != null) {
            for (SearchEntry entry : more) {
                add(entry);
            }
        }
    }

    public void clear() {
        entries.clear();
    }

    public int size() {
        return entries.size();
    }

    public List<SearchEntry> all() {
        return List.copyOf(entries);
    }

    /** Every entry in one category, in index order. */
    public List<SearchEntry> byCategory(SearchEntry.Category category) {
        List<SearchEntry> result = new ArrayList<>();
        for (SearchEntry entry : entries) {
            if (entry.category() == category) {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * The best matches for {@code query}, best first. A blank query returns an empty list
     * rather than everything, so an empty search box shows nothing (the screen shows recent
     * items instead).
     */
    public List<SearchEntry> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<Scored> scored = new ArrayList<>();
        for (SearchEntry entry : entries) {
            int score = score(entry, needle);
            if (score > 0) {
                scored.add(new Scored(entry, score));
            }
        }
        scored.sort((a, b) -> {
            int byScore = Integer.compare(b.score, a.score);
            if (byScore != 0) {
                return byScore;
            }
            int byCategory = Integer.compare(a.entry.category().ordinal(), b.entry.category().ordinal());
            if (byCategory != 0) {
                return byCategory;
            }
            return a.entry.title().compareToIgnoreCase(b.entry.title());
        });
        List<SearchEntry> results = new ArrayList<>(Math.min(MAX_RESULTS, scored.size()));
        for (int i = 0; i < scored.size() && i < MAX_RESULTS; i++) {
            results.add(scored.get(i).entry);
        }
        return results;
    }

    /** Higher is a better match; 0 means no match. */
    static int score(SearchEntry entry, String needle) {
        String title = entry.title().toLowerCase(Locale.ROOT);
        if (title.equals(needle)) {
            return 100;
        }
        if (title.startsWith(needle)) {
            return 80;
        }
        if (title.contains(needle)) {
            return 60;
        }
        String subtitle = entry.subtitle().toLowerCase(Locale.ROOT);
        if (subtitle.contains(needle)) {
            return 40;
        }
        if (entry.haystack().contains(needle)) {
            return 20;
        }
        return 0;
    }

    private record Scored(SearchEntry entry, int score) {
    }

    /** An immutable, sorted view of the entries, for a stable snapshot. */
    public List<SearchEntry> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }
}
