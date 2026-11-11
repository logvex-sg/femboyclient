package com.logvex.crystal.module.automation;

import com.logvex.crystal.automation.AutomationModule;
import com.logvex.crystal.automation.inventory.InventoryActions;
import com.logvex.crystal.automation.inventory.InventoryTransaction;
import com.logvex.crystal.automation.inventory.ItemScoring;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.player.InventoryUtil;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;

/**
 * AutoWeapon (automation): keeps the best weapon for the situation in the selected hotbar slot,
 * ranking by the item's real attack-damage attribute so swords, axes and modded weapons compare
 * correctly. It never attacks; it only chooses what the player is holding.
 *
 * <p>When no weapon is in the hotbar it can pull one in from the backpack as a transaction, so
 * the move is arbitrated like every other inventory change.
 */
public final class AutoWeaponAutomation extends AutomationModule {

    private final BooleanSetting pullFromBackpack =
            add("PullFromBackpack", "Move a weapon into the hotbar when none is carried there.", true);
    private final BooleanSetting avoidBreaking =
            add("AvoidBreaking", "Do not select a weapon below 10% durability if a better one exists.", true);
    private final IntSetting cooldown = add("Cooldown", "Minimum ticks between selections.", 10, 1, 60);

    private int ticks;

    public AutoWeaponAutomation() {
        super(Category.AUTOMATION, "AutoWeapon+", "Holds the best weapon by real attack damage.");
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
        int hotbar = bestHotbarWeapon(client);
        if (hotbar < 0) {
            if (pullFromBackpack.value() && !inventoryBusy()) {
                pullWeaponIntoHotbar(client);
            }
            return;
        }
        if (hotbar != client.player.getInventory().getSelectedSlot()) {
            InventoryUtil.selectSlot(client, hotbar);
            ticks = 0;
        }
    }

    private int bestHotbarWeapon(MinecraftClient client) {
        int best = -1;
        double bestScore = 0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = InventoryUtil.stackAt(client, slot);
            if (!isWeapon(stack)) {
                continue;
            }
            double score = ItemScoring.attackDamage(stack);
            if (avoidBreaking.value() && InventoryUtil.durabilityFraction(stack) < 0.1f) {
                score -= 100;
            }
            if (score > bestScore + 1e-6) {
                bestScore = score;
                best = slot;
            }
        }
        return best;
    }

    private void pullWeaponIntoHotbar(MinecraftClient client) {
        int source = -1;
        double bestScore = 0;
        for (int slot = InventoryUtil.BACKPACK_START; slot < InventoryUtil.MAIN_SIZE; slot++) {
            ItemStack stack = InventoryUtil.stackAt(client, slot);
            if (!isWeapon(stack)) {
                continue;
            }
            double score = ItemScoring.attackDamage(stack);
            if (score > bestScore + 1e-6) {
                bestScore = score;
                source = slot;
            }
        }
        if (source < 0) {
            return;
        }
        int target = client.player.getInventory().getSelectedSlot();
        int sourceSlot = source;
        InventoryTransaction transaction = InventoryTransaction.builder(name(), "weapon -> hotbar")
                .step("swap " + source + " -> " + target,
                        () -> InventoryActions.swapWithHotbar(client,
                                InventoryActions.screenSlotForPlayerSlot(client, sourceSlot), target),
                        () -> isWeapon(InventoryUtil.stackAt(client, target)))
                .build();
        if (submit(transaction)) {
            ticks = 0;
        }
    }

    private static boolean isWeapon(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        // Any item that adds attack damage beyond the bare-hand base counts as a weapon, so
        // modded weapons and tools with an attack bonus are all recognised.
        return ItemScoring.attackDamage(stack) > 1.0;
    }

    @Override
    public String statusLine() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return "idle";
        }
        ItemStack held = InventoryUtil.heldStack(client);
        return isWeapon(held)
                ? String.format(java.util.Locale.ROOT, "%.1f dmg", ItemScoring.attackDamage(held))
                : "no weapon held";
    }
}
