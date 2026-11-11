package com.logvex.crystal.module.automation;

import com.logvex.crystal.automation.AutomationModule;
import com.logvex.crystal.automation.inventory.InventoryActions;
import com.logvex.crystal.automation.inventory.InventoryTransaction;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.player.InventoryUtil;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;

/**
 * AutoTotem (automation): keeps a Totem of Undying in the offhand, replacing it from the hotbar
 * or backpack the moment it is consumed.
 *
 * <p>Every move is an {@link InventoryTransaction} submitted to the shared arbiter, so it can
 * never interleave with AutoArmor, AutoRefill or any other module clicking slots on the same
 * tick. The two-step move (a backpack totem into a free hotbar slot, then that slot into the
 * offhand) is one transaction, verified after each click.
 */
public final class AutoTotemAutomation extends AutomationModule {

    private final BooleanSetting refillFromBackpack =
            add("RefillFromBackpack", "Move a totem in from the backpack when the hotbar has none.", true);
    private final IntSetting cooldown = add("Cooldown", "Minimum ticks between swaps.", 4, 1, 40);

    private int ticks;

    public AutoTotemAutomation() {
        super(Category.AUTOMATION, "AutoTotem+",
                "Keeps a totem in the offhand, refilling from the hotbar or backpack.");
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
        if (InventoryUtil.offhandIs(client, Items.TOTEM_OF_UNDYING)) {
            return;
        }
        if (inventoryBusy()) {
            return;
        }
        int hotbar = InventoryUtil.findInHotbar(client, Items.TOTEM_OF_UNDYING);
        if (hotbar >= 0) {
            submitOffhandSwap(client, hotbar);
            return;
        }
        if (!refillFromBackpack.value()) {
            return;
        }
        int backpack = firstBackpackTotem(client);
        int emptyHotbar = emptyHotbarSlot(client);
        if (backpack < 0 || emptyHotbar < 0) {
            return;
        }
        submitBackpackSwap(client, backpack, emptyHotbar);
    }

    private void submitOffhandSwap(MinecraftClient client, int hotbar) {
        InventoryTransaction transaction = InventoryTransaction.builder(name(), "totem -> offhand")
                .step("swap hotbar " + hotbar + " into offhand",
                        () -> InventoryActions.swapWithOffhand(client, InventoryActions.offhandScreenSlot(client)),
                        () -> InventoryUtil.offhandIs(client, Items.TOTEM_OF_UNDYING))
                .build();
        if (submit(transaction)) {
            ticks = 0;
        }
    }

    private void submitBackpackSwap(MinecraftClient client, int backpack, int emptyHotbar) {
        InventoryTransaction transaction = InventoryTransaction.builder(name(), "totem from backpack")
                .step("backpack " + backpack + " -> hotbar " + emptyHotbar,
                        () -> InventoryActions.swapWithHotbar(client,
                                InventoryActions.screenSlotForPlayerSlot(client, backpack), emptyHotbar),
                        () -> InventoryUtil.stackAt(client, emptyHotbar).isOf(Items.TOTEM_OF_UNDYING))
                .step("hotbar " + emptyHotbar + " -> offhand",
                        () -> InventoryActions.swapWithOffhand(client, InventoryActions.offhandScreenSlot(client)),
                        () -> InventoryUtil.offhandIs(client, Items.TOTEM_OF_UNDYING))
                .build();
        if (submit(transaction)) {
            ticks = 0;
        }
    }

    private static int firstBackpackTotem(MinecraftClient client) {
        return findBackpack(client, stack -> stack.isOf(Items.TOTEM_OF_UNDYING));
    }

    @Override
    public String statusLine() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return "idle";
        }
        return InventoryUtil.offhandIs(client, Items.TOTEM_OF_UNDYING)
                ? "totem held" : "seeking totem";
    }
}
