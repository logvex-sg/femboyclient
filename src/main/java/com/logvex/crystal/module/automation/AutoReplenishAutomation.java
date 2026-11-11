package com.logvex.crystal.module.automation;

import com.logvex.crystal.automation.AutomationModule;
import com.logvex.crystal.automation.inventory.InventoryActions;
import com.logvex.crystal.automation.inventory.InventoryTransaction;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.player.InventoryUtil;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;

/**
 * AutoReplenish (automation): tops up the <em>held</em> stack (or another partial stack of the
 * same item in the hotbar) from the backpack before it runs out, so a fight never stalls on an
 * empty hand. The move is planned as a transaction so it is arbitrated with the other inventory
 * modules.
 *
 * <p>It only ever moves the exact same item into the same slot family the player already set up;
 * it never reorders the rest of the hotbar.
 */
public final class AutoReplenishAutomation extends AutomationModule {

    private final IntSetting threshold =
            add("Threshold", "Replenish a held stack once its count drops to this.", 8, 1, 63);
    private final BooleanSetting keepSameSlot =
            add("KeepSameSlot", "Only refill the slot the player is holding.", true);
    private final IntSetting cooldown = add("Cooldown", "Minimum ticks between moves.", 6, 1, 40);

    private int ticks;

    public AutoReplenishAutomation() {
        super(Category.AUTOMATION, "AutoReplenish",
                "Tops up a nearly-empty held stack from the backpack.");
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!canActUnscreened(client)) {
            return;
        }
        if (++ticks < cooldown.value()) {
            return;
        }
        if (inventoryBusy()) {
            return;
        }
        int hotbar = keepSameSlot.value()
                ? client.player.getInventory().getSelectedSlot()
                : firstLowHotbarStack(client);
        if (hotbar < 0) {
            return;
        }
        ItemStack held = InventoryUtil.stackAt(client, hotbar);
        if (held.isEmpty() || held.getCount() > threshold.value() || !held.isStackable()) {
            return;
        }
        int source = sameItemInBackpack(client, held);
        if (source < 0) {
            return;
        }
        int target = hotbar;
        InventoryTransaction transaction = InventoryTransaction
                .builder(name(), "replenish " + held.getName().getString())
                .step("merge backpack " + source + " into hotbar " + target,
                        () -> InventoryActions.swapWithHotbar(client,
                                InventoryActions.screenSlotForPlayerSlot(client, source), target),
                        () -> InventoryUtil.stackAt(client, target).getCount() > held.getCount())
                .build();
        if (submit(transaction)) {
            ticks = 0;
        }
    }

    private static int firstLowHotbarStack(MinecraftClient client) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = InventoryUtil.stackAt(client, slot);
            if (!stack.isEmpty() && stack.isStackable() && stack.getCount() <= 1) {
                return slot;
            }
        }
        return client.player.getInventory().getSelectedSlot();
    }

    private static int sameItemInBackpack(MinecraftClient client, ItemStack held) {
        for (int slot = InventoryUtil.BACKPACK_START; slot < InventoryUtil.MAIN_SIZE; slot++) {
            ItemStack stack = InventoryUtil.stackAt(client, slot);
            if (ItemStack.areItemsAndComponentsEqual(stack, held)) {
                return slot;
            }
        }
        return -1;
    }

    @Override
    public String statusLine() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return "idle";
        }
        ItemStack held = InventoryUtil.heldStack(client);
        return held.isEmpty() ? "empty hand" : held.getCount() + "x " + held.getName().getString();
    }
}
