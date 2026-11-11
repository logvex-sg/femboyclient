package com.logvex.crystal.module.automation;

import com.logvex.crystal.automation.AutomationModule;
import com.logvex.crystal.automation.inventory.InventoryActions;
import com.logvex.crystal.automation.inventory.InventorySortRules;
import com.logvex.crystal.automation.inventory.InventoryTransaction;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.player.InventoryUtil;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.EnumSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;

/**
 * AutoSort (automation): orders the player's main inventory by bucket, name, count or durability.
 *
 * <p>Sorting a 36-slot inventory by clicking is a permutation, not a set of independent moves. It
 * is executed as a single transaction of pickup/place pairs through the shared arbiter, so a sort
 * can never run half-applied alongside another module's move and can be cancelled at any step.
 */
public final class AutoSortAutomation extends AutomationModule {

    private final EnumSetting<InventorySortRules.SortKey> sortKey =
            add("SortBy", "What to order by.", InventorySortRules.SortKey.BUCKET);
    private final BooleanSetting keepHotbar =
            add("KeepHotbar", "Only sort the backpack, leaving the hotbar untouched.", false);
    private final BooleanSetting keepSelected =
            add("KeepSelected", "Never move the currently selected hotbar slot.", true);
    private final BooleanSetting autoOnClose =
            add("AutoOnContainerClose", "Re-sort once whenever a container screen closes.", false);

    private boolean wasScreenOpen;

    public AutoSortAutomation() {
        super(Category.AUTOMATION, "AutoSort",
                "Sorts the inventory by bucket, name, count or durability.");
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!canAct(client)) {
            return;
        }
        boolean screenOpen = client.currentScreen != null;
        boolean closed = wasScreenOpen && !screenOpen;
        wasScreenOpen = screenOpen;
        if (autoOnClose.value() && closed) {
            sortNow();
        }
    }

    /** Builds and submits the sort transaction; returns true when it was accepted. */
    public boolean sortNow() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!canActUnscreened(client) || inventoryBusy()) {
            return false;
        }
        int start = keepHotbar.value() ? InventoryUtil.BACKPACK_START : 0;
        int end = InventoryUtil.MAIN_SIZE;
        int size = end - start;
        ItemStack[] goal = new ItemStack[size];
        ItemStack[] cur = new ItemStack[size];
        for (int i = 0; i < size; i++) {
            cur[i] = InventoryUtil.stackAt(client, start + i);
            goal[i] = cur[i];
        }
        java.util.Arrays.sort(goal, (a, b) -> InventorySortRules.compare(a, b, sortKey.value()));

        int selected = client.player.getInventory().getSelectedSlot();
        InventoryTransaction.Builder builder = InventoryTransaction.builder(name(), "sort inventory");
        int moves = 0;
        for (int pos = 0; pos < size; pos++) {
            if (cur[pos] == goal[pos]) {
                continue;
            }
            int from = indexOf(cur, goal[pos], pos);
            if (from < 0) {
                continue;
            }
            if (keepSelected.value() && (start + pos == selected || start + from == selected)) {
                continue;
            }
            int screenFrom = InventoryActions.screenSlotForPlayerSlot(client, start + from);
            int screenTo = InventoryActions.screenSlotForPlayerSlot(client, start + pos);
            if (screenFrom < 0 || screenTo < 0) {
                continue;
            }
            final int toSlot = start + pos;
            final ItemStack moved = cur[from];
            builder.step("move slot " + (start + from) + " -> " + toSlot,
                    () -> {
                        InventoryActions.pickup(client, screenFrom);
                        InventoryActions.pickup(client, screenTo);
                    },
                    () -> {
                        ItemStack landed = InventoryUtil.stackAt(client, toSlot);
                        return !landed.isEmpty() || moved.isEmpty();
                    });
            ItemStack tmp = cur[pos];
            cur[pos] = cur[from];
            cur[from] = tmp;
            moves++;
        }
        if (moves == 0) {
            context().notifications().info("Inventory already sorted \u2661");
            return false;
        }
        if (!submit(builder.build())) {
            return false;
        }
        context().notifications().success("Sorting inventory (" + moves + " moves)");
        return true;
    }

    /** The position at or after {@code from} holding an identity- or value-equal stack, or -1. */
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
        return "by " + sortKey.value().name().toLowerCase(java.util.Locale.ROOT);
    }
}
