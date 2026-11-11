package com.logvex.crystal.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ItemDatabaseTest {

    private final ItemDatabase db = ItemDatabase.get();

    @Test
    void catalogueIsNonEmptyAndHasCategories() {
        assertTrue(db.size() >= 30, "catalogue should hold a useful number of rows");
        assertTrue(db.categories().contains("combat"));
        assertTrue(db.categories().contains("building"));
    }

    @Test
    void lookupByIdIsCaseInsensitive() {
        var entry = db.byId("MINECRAFT:END_CRYSTAL");
        assertNotNull(entry);
        assertEquals("End Crystal", entry.name());
        assertEquals("combat", entry.category());
    }

    @Test
    void unknownIdFallsBackToIdAndDefaultStack() {
        assertNull(db.byId("minecraft:not_a_real_item"));
        assertEquals("minecraft:not_a_real_item", db.nameOf("minecraft:not_a_real_item"));
        assertEquals(64, db.maxStackOf("minecraft:not_a_real_item"));
    }

    @Test
    void maxStackReflectsNonStackables() {
        assertEquals(1, db.maxStackOf("minecraft:totem_of_undying"));
        assertEquals(16, db.maxStackOf("minecraft:ender_pearl"));
    }

    @Test
    void searchMatchesIdAndNameAndBlankMatchesAll() {
        assertEquals(db.size(), db.search("").size());
        assertEquals(db.size(), db.search(null).size());
        assertTrue(db.search("crystal").stream()
                .anyMatch(e -> e.id().equals("minecraft:end_crystal")));
        assertTrue(db.search("golden apple").stream()
                .anyMatch(e -> e.id().equals("minecraft:golden_apple")));
        assertTrue(db.search("zzz-no-match").isEmpty());
    }

    @Test
    void categoryFilterIsCaseInsensitive() {
        List<ItemDatabase.Entry> potions = db.byCategory("POTION");
        assertFalse(potions.isEmpty());
        assertTrue(potions.stream().allMatch(e -> e.category().equals("potion")));
    }

    @Test
    void entryRejectsBlankIdAndNormalizesDefaults() {
        assertThrows(IllegalArgumentException.class, () -> new ItemDatabase.Entry("  ", "x", "y", 1));
        var entry = new ItemDatabase.Entry("minecraft:foo", "", "", 0);
        assertEquals("minecraft:foo", entry.name());
        assertEquals("misc", entry.category());
        assertEquals(64, entry.maxStack());
    }
}
