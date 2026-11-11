package com.logvex.crystal.module.automation;

import com.logvex.crystal.automation.AutomationModule;
import com.logvex.crystal.automation.inventory.InventoryActions;
import com.logvex.crystal.automation.inventory.InventorySortRules;
import com.logvex.crystal.automation.inventory.InventoryTransaction;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.EnumSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;

/**
 * Shulker Organizer (automation): sorts the contents of an <em>open</em> shulker box (or any
 * generic container) into buckets, using the same rules as {@link AutoSortAutomation} but applied
 * to the container's own slots.
 *
 * <p>It only touches the container the player has legitimately opened, and only its own slots, so
 * the player's inventory is left exactly as it was. The whole reorder is one transaction.
 */
public final class ShulkerOrganizerAutomation extends AutomationModule {

    private final EnumSetting<InventorySortRules.SortKey> sortKey =
            add("SortBy", "What to order the container contents by.", InventorySortRules.SortKey.BUCKET);
    private final BooleanSetting onOpen =
            add("OnOpen", "Sort once each time a container is opened.", false);

    private int lastSyncId = Integer.MIN_VALUE;

    public ShulkerOrganizerAutomation() {
        super(Category.AUTOMATION, "ShulkerOrganizer",
                "Sorts the open shulker box or container by bucket.");
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!canAct(client) || !onOpen.value()) {
            return;
        }
        ScreenHandler handler = InventoryActions.handler(client);
        if (handler == null) {
            return;
        }
        if (handler.syncId != lastSyncId) {
            lastSyncId = handler.syncId;
            if (handler instanceof GenericContainerScreenHandler) {
                organize();
            }
        }
    }

    /** Sorts the open container's slots; returns true when a sort transaction was submitted. */
    public boolean organize() {
        MinecraftClient client = MinecraftClient.getInstance();
        ScreenHandler handler = InventoryActions.handler(client);
        if (handler == null || !(handler instanceof GenericContainerScreenHandler) || inventoryBusy()) {
            return false;
        }
        int size = InventoryActions.containerSlots(handler);
        if (size <= 0) {
            return false;
        }
        ItemStack[] goal = new ItemStack[size];
        ItemStack[] cur = new ItemStack[size];
        for (int i = 0; i < size; i++) {
            cur[i] = InventoryActions.containerStack(handler, i);
            goal[i] = cur[i];
        }
        java.util.Arrays.sort(goal, (a, b) -> InventorySortRules.compare(a, b, sortKey.value()));

        InventoryTransaction.Builder builder = InventoryTransaction.builder(name(), "organize container");
        int moves = 0;
        for (int pos = 0; pos < size; pos++) {
            if (cur[pos] == goal[pos]) {
                continue;
            }
            int from = indexOf(cur, goal[pos], pos);
            if (from < 0) {
                continue;
            }
            final int toSlot = pos;
            final ItemStack moved = cur[from];
            builder.step("container " + from + " -> " + pos,
                    () -> {
                        InventoryActions.pickup(client, from);
                        InventoryActions.pickup(client, toSlot);
                    },
                    () -> {
                        ItemStack landed = InventoryActions.containerStack(
                                InventoryActions.handler(client), toSlot);
                        return !landed.isEmpty() || moved.isEmpty();
                    });
            ItemStack tmp = cur[pos];
            cur[pos] = cur[from];
            cur[from] = tmp;
            moves++;
        }
        if (moves == 0) {
            return false;
        }
        if (!submit(builder.build())) {
            return false;
        }
        context().notifications().success("Organizing container (" + moves + " moves)");
        return true;
    }

    private static int indexOf(ItemStack[] cur, ItemStack want, int from) {
        for (int i = from; i < cur.length; i++) {
            if (cur[i] == want) {
                return i;
            }
        }
        for (int i = from; i < cur.length; i++) {
            if (cur[i].getItem() == want.getItem() && cur[i].getCount() == want.getCount()) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public String statusLine() {
        MinecraftClient client = MinecraftClient.getInstance();
        ScreenHandler handler = InventoryActions.handler(client);
        int slots = InventoryActions.containerSlots(handler);
        return slots > 0 ? slots + " slots open" : "no container";
    }
}
