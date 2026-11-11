package com.logvex.crystal.storage;

import com.logvex.crystal.automation.inventory.InventoryActions;
import com.logvex.crystal.player.InventoryUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;

/**
 * Builds a {@link StorageSnapshot} from inventories the player can legitimately see: their own
 * inventory, always, plus the slots of a container screen the server has already opened for
 * them. Nothing here reads server-side state that is not already delivered to the client, and
 * there is no discovery of other players' storage.
 *
 * <p>The scan is a bounded walk of the open {@link ScreenHandler}: the player inventory is 36
 * slots and a container screen is at most a few dozen more, so a single call is cheap enough to
 * run on demand from a tool action rather than every frame.
 */
public final class StorageScanner {

    private StorageScanner() {
    }

    /**
     * Snapshots the player inventory and, when a container is open, its contents.
     *
     * @param client         the live client
     * @param protectedItems items to flag as protected (never sorted or trashed)
     * @return the snapshot, empty when there is no player
     */
    public static StorageSnapshot scan(MinecraftClient client, Set<Item> protectedItems) {
        if (client == null || client.player == null) {
            return StorageSnapshot.empty();
        }
        Set<Item> protectedSet = protectedItems == null ? Set.of() : protectedItems;
        List<StorageSnapshot.Entry> entries = new ArrayList<>();

        var inventory = client.player.getInventory();
        for (int slot = 0; slot < InventoryUtil.MAIN_SIZE; slot++) {
            add(entries, "player", slot, inventory.getStack(slot), protectedSet);
        }

        ScreenHandler handler = InventoryActions.handler(client);
        if (handler != null && InventoryActions.containerOpen(client)) {
            int containerSlots = InventoryActions.containerSlots(handler);
            for (int slot = 0; slot < containerSlots; slot++) {
                add(entries, "container", slot, InventoryActions.containerStack(handler, slot), protectedSet);
            }
        }
        return StorageSnapshot.of(entries);
    }

    private static void add(List<StorageSnapshot.Entry> entries, String container, int slot,
            ItemStack stack, Set<Item> protectedItems) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        String id = Registries.ITEM.getId(stack.getItem()).toString();
        addId(entries, container, slot, id, stack.getCount(), protectedItems.contains(stack.getItem()));
    }

    /**
     * Appends one non-empty stack to the entry list. Package-private and free of game types so
     * the scan's decisions (skipping empties, flagging protected stacks) are unit-testable.
     */
    static void addId(List<StorageSnapshot.Entry> entries, String container, int slot, String itemId,
            int count, boolean protectedItem) {
        if (itemId == null || itemId.isBlank() || count <= 0) {
            return;
        }
        entries.add(new StorageSnapshot.Entry(container, slot, itemId, count, protectedItem));
    }
}
