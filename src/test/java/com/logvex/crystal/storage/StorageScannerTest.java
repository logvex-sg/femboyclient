package com.logvex.crystal.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The storage scanner's per-stack decisions. The live scan needs a Minecraft client, but the
 * rule that decides whether a stack enters the snapshot is pure and is checked here.
 */
class StorageScannerTest {

    @Test
    void emptyStacksAreSkipped() {
        List<StorageSnapshot.Entry> entries = new ArrayList<>();
        StorageScanner.addId(entries, "player", 0, "minecraft:air", 0, false);
        StorageScanner.addId(entries, "player", 1, "", 5, false);
        StorageScanner.addId(entries, "player", 2, null, 5, false);
        assertTrue(entries.isEmpty());
    }

    @Test
    void nonEmptyStacksAreRecordedWithTheirProtectionFlag() {
        List<StorageSnapshot.Entry> entries = new ArrayList<>();
        StorageScanner.addId(entries, "player", 3, "minecraft:stone", 64, false);
        StorageScanner.addId(entries, "container", 0, "minecraft:totem_of_undying", 1, true);
        assertEquals(2, entries.size());
        assertEquals("minecraft:stone", entries.get(0).itemId());
        assertEquals(64, entries.get(0).count());
        assertTrue(entries.get(1).protectedItem());
    }
}
