package com.logvex.crystal.automation.inventory;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

/**
 * Pure rules for the trash filter: decide whether a stack may be dropped.
 *
 * <p>Safety first: a stack is only trash when its item is on the drop list <em>and</em> it is not
 * protected, not enchanted, not renamed, and not a shulker box or container holding anything.
 * That makes "protected item" and "explicitly listed" both necessary, so a mis-typed list can
 * never drop something valuable. Kept Minecraft-registry-only (no client state) for testing.
 */
public final class TrashRules {

    private TrashRules() {
    }

    /** The item id of a stack, e.g. {@code minecraft:cobblestone}, or "" for an empty stack. */
    public static String idOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return "";
        }
        var key = Registries.ITEM.getId(stack.getItem());
        return key == null ? "" : key.toString();
    }

    /** Normalises a user-entered item list (comma or newline separated) to lowercase ids. */
    public static Set<String> parseList(String raw) {
        Set<String> result = new LinkedHashSet<>();
        if (raw == null) {
            return result;
        }
        for (String token : raw.split("[,\\n]")) {
            String trimmed = token.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) {
                result.add(trimmed.contains(":") ? trimmed : "minecraft:" + trimmed);
            }
        }
        return result;
    }

    /** The hard safety gate: some stacks may never be dropped by the filter. */
    public static boolean protectedFromDrop(ItemStack stack, Set<String> protectedIds) {
        if (stack.isEmpty()) {
            return true;
        }
        if (protectedIds.contains(idOf(stack))) {
            return true;
        }
        if (stack.hasEnchantments()) {
            return true;
        }
        if (stack.getCustomName() != null) {
            return true;
        }
        // A container holding anything is not empty trash.
        var container = stack.get(net.minecraft.component.DataComponentTypes.CONTAINER);
        if (container != null && container.streamNonEmpty().findAny().isPresent()) {
            return true;
        }
        // Never drop an equipped or otherwise unique item by accident.
        return false;
    }

    /** True when the stack is on the trash list and passes the protection gate. */
    public static boolean isTrash(ItemStack stack, Set<String> trashIds, Set<String> protectedIds) {
        if (stack.isEmpty() || !trashIds.contains(idOf(stack))) {
            return false;
        }
        return !protectedFromDrop(stack, protectedIds);
    }
}
