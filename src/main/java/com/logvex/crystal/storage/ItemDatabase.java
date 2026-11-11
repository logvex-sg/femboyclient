package com.logvex.crystal.storage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A searchable, offline catalogue of item ids.
 *
 * <p>Everything here is plain data: ids, human-readable names, a category and the max stack
 * size. It deliberately imports no Minecraft classes so the storage and material tools can
 * resolve an id to a name (and back) without a live registry, and so it can be unit-tested
 * without a game bootstrap.
 *
 * <p>Lookups are case-insensitive; a query matches when it appears in the id or the name.
 */
public final class ItemDatabase {

    /** One catalogue row. */
    public record Entry(String id, String name, String category, int maxStack) {
        public Entry {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("item id must not be blank");
            }
            name = name == null || name.isBlank() ? id : name;
            category = category == null || category.isBlank() ? "misc" : category;
            if (maxStack < 1) {
                maxStack = 64;
            }
        }

        /** True when the query matches the id or the display name. */
        boolean matches(String needle) {
            return id.toLowerCase(Locale.ROOT).contains(needle)
                    || name.toLowerCase(Locale.ROOT).contains(needle);
        }
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry("minecraft:end_crystal", "End Crystal", "combat", 64),
            new Entry("minecraft:obsidian", "Obsidian", "building", 64),
            new Entry("minecraft:totem_of_undying", "Totem of Undying", "combat", 1),
            new Entry("minecraft:ender_pearl", "Ender Pearl", "combat", 16),
            new Entry("minecraft:netherite_sword", "Netherite Sword", "weapon", 1),
            new Entry("minecraft:diamond_sword", "Diamond Sword", "weapon", 1),
            new Entry("minecraft:netherite_pickaxe", "Netherite Pickaxe", "tool", 1),
            new Entry("minecraft:diamond_pickaxe", "Diamond Pickaxe", "tool", 1),
            new Entry("minecraft:netherite_axe", "Netherite Axe", "tool", 1),
            new Entry("minecraft:shield", "Shield", "combat", 1),
            new Entry("minecraft:golden_apple", "Golden Apple", "food", 64),
            new Entry("minecraft:enchanted_golden_apple", "Enchanted Golden Apple", "food", 64),
            new Entry("minecraft:cooked_beef", "Steak", "food", 64),
            new Entry("minecraft:bread", "Bread", "food", 64),
            new Entry("minecraft:potion", "Potion", "potion", 1),
            new Entry("minecraft:splash_potion", "Splash Potion", "potion", 1),
            new Entry("minecraft:lingering_potion", "Lingering Potion", "potion", 1),
            new Entry("minecraft:netherite_helmet", "Netherite Helmet", "armor", 1),
            new Entry("minecraft:netherite_chestplate", "Netherite Chestplate", "armor", 1),
            new Entry("minecraft:netherite_leggings", "Netherite Leggings", "armor", 1),
            new Entry("minecraft:netherite_boots", "Netherite Boots", "armor", 1),
            new Entry("minecraft:elytra", "Elytra", "armor", 1),
            new Entry("minecraft:shulker_box", "Shulker Box", "storage", 1),
            new Entry("minecraft:chest", "Chest", "storage", 64),
            new Entry("minecraft:barrel", "Barrel", "storage", 64),
            new Entry("minecraft:ender_chest", "Ender Chest", "storage", 64),
            new Entry("minecraft:hopper", "Hopper", "storage", 64),
            new Entry("minecraft:cobblestone", "Cobblestone", "building", 64),
            new Entry("minecraft:stone", "Stone", "building", 64),
            new Entry("minecraft:oak_planks", "Oak Planks", "building", 64),
            new Entry("minecraft:glass", "Glass", "building", 64),
            new Entry("minecraft:redstone", "Redstone Dust", "redstone", 64),
            new Entry("minecraft:redstone_block", "Block of Redstone", "redstone", 64),
            new Entry("minecraft:observer", "Observer", "redstone", 64),
            new Entry("minecraft:piston", "Piston", "redstone", 64),
            new Entry("minecraft:sticky_piston", "Sticky Piston", "redstone", 64),
            new Entry("minecraft:torch", "Torch", "light", 64),
            new Entry("minecraft:glowstone", "Glowstone", "light", 64),
            new Entry("minecraft:sea_lantern", "Sea Lantern", "light", 64),
            new Entry("minecraft:ender_eye", "Eye of Ender", "misc", 64),
            new Entry("minecraft:firework_rocket", "Firework Rocket", "misc", 64),
            new Entry("minecraft:water_bucket", "Water Bucket", "misc", 1),
            new Entry("minecraft:lava_bucket", "Lava Bucket", "misc", 1),
            new Entry("minecraft:experience_bottle", "Bottle o' Enchanting", "misc", 64));

    private final Map<String, Entry> byId;

    public ItemDatabase() {
        Map<String, Entry> map = new java.util.LinkedHashMap<>();
        for (Entry entry : ENTRIES) {
            map.put(entry.id().toLowerCase(Locale.ROOT), entry);
        }
        this.byId = Map.copyOf(map);
    }

    /** A shared catalogue; the data is immutable so one instance is enough. */
    private static final ItemDatabase INSTANCE = new ItemDatabase();

    public static ItemDatabase get() {
        return INSTANCE;
    }

    /** How many rows the catalogue holds. */
    public int size() {
        return ENTRIES.size();
    }

    /** Every distinct category, in first-seen order. */
    public Set<String> categories() {
        Set<String> result = new LinkedHashSet<>();
        for (Entry entry : ENTRIES) {
            result.add(entry.category());
        }
        return result;
    }

    /** The row for an id, or {@code null} when it is not catalogued. */
    public Entry byId(String id) {
        if (id == null) {
            return null;
        }
        return byId.get(id.toLowerCase(Locale.ROOT));
    }

    /** The display name for an id, or the id itself when unknown. */
    public String nameOf(String id) {
        Entry entry = byId(id);
        return entry == null ? id : entry.name();
    }

    /** The max stack size for an id, defaulting to 64 when unknown. */
    public int maxStackOf(String id) {
        Entry entry = byId(id);
        return entry == null ? 64 : entry.maxStack();
    }

    /** Rows whose id or name contains the query, case-insensitively. A blank query matches all. */
    public List<Entry> search(String query) {
        String needle = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        List<Entry> result = new ArrayList<>();
        for (Entry entry : ENTRIES) {
            if (needle.isEmpty() || entry.matches(needle)) {
                result.add(entry);
            }
        }
        return result;
    }

    /** Rows in a category, case-insensitively. */
    public List<Entry> byCategory(String category) {
        String needle = category == null ? "" : category.toLowerCase(Locale.ROOT).trim();
        List<Entry> result = new ArrayList<>();
        for (Entry entry : ENTRIES) {
            if (entry.category().equalsIgnoreCase(needle)) {
                result.add(entry);
            }
        }
        return result;
    }
}
