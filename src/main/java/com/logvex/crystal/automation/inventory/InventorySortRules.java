package com.logvex.crystal.automation.inventory;

import java.util.List;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;

/**
 * Pure rules for ordering inventory slots: which bucket an item belongs to and how two stacks
 * compare. Kept free of any client state so the ordering is unit tested directly and the sorting
 * module only has to turn a plan into clicks.
 */
public final class InventorySortRules {

    /** The buckets items are grouped into, in display order. */
    public enum Bucket {
        BLOCKS,
        TOOLS,
        WEAPONS,
        ARMOR,
        FOOD,
        POTIONS,
        MATERIALS,
        MISC
    }

    /** What to sort by within the chosen ordering. */
    public enum SortKey {
        BUCKET,
        NAME,
        COUNT,
        DURABILITY
    }

    public static final List<Bucket> BUCKET_ORDER = List.of(Bucket.values());

    private InventorySortRules() {
    }

    /** The bucket an item stack belongs to, from its real components. */
    public static Bucket bucketOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return Bucket.MISC;
        }
        if (stack.contains(DataComponentTypes.FOOD)) {
            return Bucket.FOOD;
        }
        if (stack.contains(DataComponentTypes.POTION_CONTENTS)) {
            return Bucket.POTIONS;
        }
        var equippable = stack.get(DataComponentTypes.EQUIPPABLE);
        if (equippable != null && equippable.slot().isArmorSlot()) {
            return Bucket.ARMOR;
        }
        if (stack.contains(DataComponentTypes.TOOL)) {
            return Bucket.TOOLS;
        }
        if (ItemScoring.attackDamage(stack) > 1.0) {
            return Bucket.WEAPONS;
        }
        if (stack.getItem() instanceof BlockItem) {
            return Bucket.BLOCKS;
        }
        if (stack.isDamageable() || stack.getMaxCount() > 1) {
            return Bucket.MATERIALS;
        }
        return Bucket.MISC;
    }

    /**
     * Compares two non-empty stacks for the given key. Empty stacks always sort last, so the
     * sort packs items towards the front of the range and leaves empty slots at the back.
     */
    public static int compare(ItemStack a, ItemStack b, SortKey key) {
        boolean ea = a.isEmpty();
        boolean eb = b.isEmpty();
        if (ea || eb) {
            return Boolean.compare(ea, eb);
        }
        int bucket = Integer.compare(BUCKET_ORDER.indexOf(bucketOf(a)), BUCKET_ORDER.indexOf(bucketOf(b)));
        return switch (key) {
            case BUCKET -> bucket != 0 ? bucket : compareByName(a, b);
            case NAME -> {
                int byName = compareByName(a, b);
                yield byName != 0 ? byName : bucket;
            }
            case COUNT -> {
                int byCount = Integer.compare(b.getCount(), a.getCount());
                yield byCount != 0 ? byCount : compareByName(a, b);
            }
            case DURABILITY -> {
                int byDurability = Float.compare(
                        com.logvex.crystal.player.InventoryUtil.durabilityFraction(b),
                        com.logvex.crystal.player.InventoryUtil.durabilityFraction(a));
                yield byDurability != 0 ? byDurability : compareByName(a, b);
            }
        };
    }

    private static int compareByName(ItemStack a, ItemStack b) {
        int byName = a.getName().getString().compareToIgnoreCase(b.getName().getString());
        if (byName != 0) {
            return byName;
        }
        // Same display name (e.g. two potions): order by the stable item id so the sort is total.
        return a.getItem().toString().compareTo(b.getItem().toString());
    }

    /**
     * A stable key for a stack that ignores slot position, used to detect duplicates and to plan
     * moves. Empty stacks produce the empty string.
     */
    public static String keyOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return "";
        }
        return stack.getItem().toString() + "#" + stack.getComponentChanges().hashCode();
    }
}
