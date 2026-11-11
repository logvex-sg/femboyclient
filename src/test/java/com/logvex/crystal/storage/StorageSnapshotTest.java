package com.logvex.crystal.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Covers the storage snapshot: totals, duplicates and container grouping. */
class StorageSnapshotTest {

    private static StorageSnapshot snapshot() {
        return StorageSnapshot.of(List.of(
                new StorageSnapshot.Entry("player", 0, "minecraft:stone", 64, false),
                new StorageSnapshot.Entry("player", 1, "minecraft:dirt", 12, false),
                new StorageSnapshot.Entry("chest@10,64,10", 3, "minecraft:stone", 32, false),
                new StorageSnapshot.Entry("chest@10,64,10", 4, "minecraft:diamond", 1, true)));
    }

    @Test
    void totalsSumAcrossContainersAndPreserveOrder() {
        Map<String, Integer> totals = snapshot().totals();
        assertEquals(96, totals.get("minecraft:stone"));
        assertEquals(12, totals.get("minecraft:dirt"));
        assertEquals(1, totals.get("minecraft:diamond"));
    }

    @Test
    void duplicatesReportsItemsHeldInMoreThanOneStack() {
        Map<String, List<StorageSnapshot.Entry>> duplicates = snapshot().duplicates();
        assertEquals(1, duplicates.size());
        assertEquals(2, duplicates.get("minecraft:stone").size());
    }

    @Test
    void containerGroupingAndCounts() {
        StorageSnapshot snapshot = snapshot();
        assertEquals(List.of("player", "chest@10,64,10"), snapshot.containers());
        assertEquals(2, snapshot.occupied("player"));
        assertEquals(2, snapshot.occupied("chest@10,64,10"));
        assertEquals(0, snapshot.occupied("absent"));
    }

    @Test
    void stacksOfReturnsEveryMatchingStack() {
        assertEquals(2, snapshot().stacksOf("minecraft:stone").size());
        assertTrue(snapshot().stacksOf("minecraft:netherite_ingot").isEmpty());
    }

    @Test
    void countsAndDescribeAreConsistent() {
        StorageSnapshot snapshot = snapshot();
        assertEquals(4, snapshot.stackCount());
        assertEquals(109, snapshot.itemCount());
        assertEquals("2 container(s), 4 stack(s), 109 item(s)", snapshot.describe());
        assertTrue(StorageSnapshot.empty().isEmpty());
    }
}
