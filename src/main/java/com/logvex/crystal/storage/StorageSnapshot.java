package com.logvex.crystal.storage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A pure-data view of the item stacks the client can legitimately see right now: the player's own
 * inventory, plus any container the player has <em>opened</em>.
 *
 * <p>This is the storage-inspection core behind the container viewer, the item requirement
 * calculator, the material totals, duplicate-stack detection and the transfer planner. It holds
 * only stacks the client was already sent - it never discovers, enumerates or remembers another
 * player's storage, and it does not persist anything between screens.
 */
public final class StorageSnapshot {

    /** One stack: which container it is in, its slot, its item id and its count. */
    public record Entry(String container, int slot, String itemId, int count, boolean protectedItem) {
        public Entry {
            Objects.requireNonNull(container, "container");
            Objects.requireNonNull(itemId, "itemId");
        }
    }

    private final List<Entry> entries;

    private StorageSnapshot(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    public static StorageSnapshot of(List<Entry> entries) {
        return new StorageSnapshot(entries == null ? List.of() : entries);
    }

    public static StorageSnapshot empty() {
        return new StorageSnapshot(List.of());
    }

    public List<Entry> entries() {
        return entries;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Total item count per item id, preserving first-seen order. */
    public Map<String, Integer> totals() {
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (Entry entry : entries) {
            totals.merge(entry.itemId(), entry.count(), Integer::sum);
        }
        return totals;
    }

    /** Every stack of one item id, in scan order. */
    public List<Entry> stacksOf(String itemId) {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.itemId().equals(itemId)) {
                result.add(entry);
            }
        }
        return result;
    }

    /** The number of occupied slots in a named container. */
    public int occupied(String container) {
        int count = 0;
        for (Entry entry : entries) {
            if (entry.container().equals(container)) {
                count++;
            }
        }
        return count;
    }

    /** The distinct container names in the snapshot, in first-seen order. */
    public List<String> containers() {
        List<String> names = new ArrayList<>();
        for (Entry entry : entries) {
            if (!names.contains(entry.container())) {
                names.add(entry.container());
            }
        }
        return names;
    }

    /**
     * Item ids held in more than one stack - the "duplicate stack" report. Protected items are
     * included in the report but flagged, so the GUI can grey them out rather than suggesting a
     * merge the user forbade.
     */
    public Map<String, List<Entry>> duplicates() {
        Map<String, List<Entry>> grouped = new LinkedHashMap<>();
        for (Entry entry : entries) {
            grouped.computeIfAbsent(entry.itemId(), key -> new ArrayList<>()).add(entry);
        }
        grouped.entrySet().removeIf(entry -> entry.getValue().size() < 2);
        return grouped;
    }

    /** The total number of item stacks (not items) the player holds. */
    public int stackCount() {
        return entries.size();
    }

    /** The total number of items across every stack. */
    public int itemCount() {
        int total = 0;
        for (Entry entry : entries) {
            total += Math.max(0, entry.count());
        }
        return total;
    }

    /** A one-line summary for the container viewer header. */
    public String describe() {
        return containers().size() + " container(s), " + stackCount() + " stack(s), "
                + itemCount() + " item(s)";
    }
}
