package com.logvex.crystal.module.automation;

import com.logvex.crystal.automation.AutomationModule;
import com.logvex.crystal.automation.inventory.InventoryActions;
import com.logvex.crystal.automation.inventory.InventoryTransaction;
import com.logvex.crystal.automation.inventory.ItemScoring;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.player.InventoryUtil;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.EnumSetting;
import com.logvex.crystal.setting.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

/**
 * AutoArmor (automation): equips the best armour carried, comparing pieces by their real armour
 * attribute rather than a hand-written tier table, so modded or enchanted pieces rank correctly.
 *
 * <p>Armour priority is configurable: {@code TOUGHNESS_FIRST} keeps the most protective piece on
 * even when a slightly better one would fit, {@code POINTS_ONLY} swaps purely on armour points,
 * and {@code PRESERVE} only fills empty or badly worn slots. Every equip is one transaction.
 */
public final class AutoArmorAutomation extends AutomationModule {

    /** How aggressively to replace a worn piece. */
    public enum Priority {
        POINTS_ONLY,
        TOUGHNESS_FIRST,
        PRESERVE
    }

    private final EnumSetting<Priority> priority =
            add("Priority", "How to choose between two pieces.", Priority.POINTS_ONLY);
    private final IntSetting wearThreshold = add("WearThreshold",
            "Replace a worn piece below this durability percentage.", 15, 0, 100);
    private final BooleanSetting ignoreWorn =
            add("IgnoreWorn", "Never replace a piece above the wear threshold.", false);
    private final IntSetting cooldown = add("Cooldown", "Minimum ticks between equips.", 5, 1, 40);

    private int ticks;

    public AutoArmorAutomation() {
        super(Category.AUTOMATION, "AutoArmor+",
                "Equips the best armour carried by its real armour attribute.");
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
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            int best = bestSlotFor(client, slot);
            if (best < 0) {
                continue;
            }
            int screen = InventoryActions.screenSlotForPlayerSlot(client, best);
            if (screen < 0) {
                continue;
            }
            ItemStack worn = client.player.getEquippedStack(slot);
            InventoryTransaction transaction = InventoryTransaction
                    .builder(name(), "equip " + slot.getName())
                    .step("quick-move " + best + " onto " + slot.getName(),
                            () -> InventoryActions.quickMove(client, screen),
                            () -> betterWorn(client, slot, worn))
                    .build();
            if (submit(transaction)) {
                ticks = 0;
                return;
            }
        }
    }

    /** True once the worn piece has changed (the quick-move took effect). */
    private static boolean betterWorn(MinecraftClient client, EquipmentSlot slot, ItemStack before) {
        ItemStack now = client.player.getEquippedStack(slot);
        return now != before && !now.isEmpty();
    }

    /** The main-inventory slot of the best piece for {@code slot}, or -1 when none improves it. */
    private int bestSlotFor(MinecraftClient client, EquipmentSlot slot) {
        ItemStack worn = client.player.getEquippedStack(slot);
        double wornScore = ItemScoring.armorPoints(worn, slot);
        boolean wornEmpty = worn.isEmpty();
        boolean wornBad = InventoryUtil.durabilityFraction(worn) * 100f < wearThreshold.value();
        if (!wornEmpty && !wornBad && ignoreWorn.value()) {
            return -1;
        }
        int best = -1;
        double bestScore = wornEmpty ? -1 : wornScore;
        for (int i = 0; i < InventoryUtil.MAIN_SIZE; i++) {
            ItemStack stack = InventoryUtil.stackAt(client, i);
            if (stack.isEmpty()) {
                continue;
            }
            if (!equipsTo(stack, slot)) {
                continue;
            }
            if (!wornEmpty && !wornBad && !swapAllowed()) {
                continue;
            }
            double score = ItemScoring.armorPoints(stack, slot);
            if (score > bestScore + 1e-6) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    private boolean swapAllowed() {
        return priority.value() == Priority.POINTS_ONLY || priority.value() == Priority.TOUGHNESS_FIRST;
    }

    private static boolean equipsTo(ItemStack stack, EquipmentSlot slot) {
        var equippable = stack.get(net.minecraft.component.DataComponentTypes.EQUIPPABLE);
        return equippable != null && equippable.slot() == slot;
    }

    @Override
    public String statusLine() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return "idle";
        }
        double total = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            total += ItemScoring.armorPoints(client.player.getEquippedStack(slot), slot);
        }
        return String.format(java.util.Locale.ROOT, "%.0f armour", total);
    }
}
