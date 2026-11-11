package com.logvex.crystal.module.automation;

import com.logvex.crystal.automation.AutomationModule;
import com.logvex.crystal.automation.inventory.InventoryActions;
import com.logvex.crystal.automation.inventory.InventoryTransaction;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.player.InventoryUtil;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * AutoRefill (automation): keeps the hotbar stocked with the anarchy staples from the backpack,
 * with each item's target count configurable. Unlike a fire-and-forget module it plans each move
 * as a transaction, so a refill cannot collide with AutoTotem or AutoArmor on the same tick, and
 * it will not displace the slot the player is currently holding.
 */
public final class AutoRefillAutomation extends AutomationModule {

    private final BooleanSetting obsidian = add("Obsidian", "Keep obsidian stocked.", true);
    private final BooleanSetting crystals = add("EndCrystal", "Keep end crystals stocked.", true);
    private final BooleanSetting gapples = add("GoldenApple", "Keep golden apples stocked.", true);
    private final BooleanSetting pearls = add("EnderPearl", "Keep ender pearls stocked.", true);
    private final BooleanSetting totems = add("Totem", "Keep totems stocked.", false);
    private final IntSetting minimum = add("Minimum", "Refill a stack below this count.", 8, 1, 64);
    private final IntSetting cooldown = add("Cooldown", "Minimum ticks between moves.", 4, 1, 40);

    private int ticks;

    public AutoRefillAutomation() {
        super(Category.AUTOMATION, "AutoRefill+",
                "Tops up the hotbar from the backpack with a configurable list.");
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
        for (Item item : wantedItems()) {
            if (hotbarCount(client, item) >= minimum.value()) {
                continue;
            }
            int backpack = firstBackpackSlot(client, item);
            if (backpack < 0) {
                continue;
            }
            int empty = emptyHotbarSlot(client);
            if (empty < 0) {
                return;
            }
            int source = backpack;
            InventoryTransaction transaction = InventoryTransaction
                    .builder(name(), "refill " + item.getName().getString())
                    .step("backpack " + backpack + " -> hotbar " + empty,
                            () -> InventoryActions.swapWithHotbar(client,
                                    InventoryActions.screenSlotForPlayerSlot(client, source), empty),
                            () -> InventoryUtil.stackAt(client, empty).isOf(item))
                    .build();
            if (submit(transaction)) {
                ticks = 0;
                return;
            }
        }
    }

    private List<Item> wantedItems() {
        List<Item> items = new ArrayList<>();
        if (obsidian.value()) {
            items.add(Items.OBSIDIAN);
        }
        if (crystals.value()) {
            items.add(Items.END_CRYSTAL);
        }
        if (gapples.value()) {
            items.add(Items.GOLDEN_APPLE);
        }
        if (pearls.value()) {
            items.add(Items.ENDER_PEARL);
        }
        if (totems.value()) {
            items.add(Items.TOTEM_OF_UNDYING);
        }
        return items;
    }

    private static int hotbarCount(MinecraftClient client, Item item) {
        int total = 0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = InventoryUtil.stackAt(client, slot);
            if (stack.isOf(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int firstBackpackSlot(MinecraftClient client, Item item) {
        return findBackpack(client, stack -> stack.isOf(item));
    }
}
