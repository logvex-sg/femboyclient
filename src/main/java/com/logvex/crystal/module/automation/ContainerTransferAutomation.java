package com.logvex.crystal.module.automation;

import com.logvex.crystal.automation.AutomationModule;
import com.logvex.crystal.automation.inventory.InventoryActions;
import com.logvex.crystal.automation.inventory.TrashRules;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.EnumSetting;
import com.logvex.crystal.setting.StringSetting;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;

/**
 * Container Transfer Assistant (automation): moves whole stacks between the player inventory and
 * an <em>open</em> container, in either direction, filtered by an optional item list.
 *
 * <p>It only ever touches the container the player legitimately opened, and each stack moved is
 * one transaction step, so a transfer can never collide with a sort or a refill. Nothing is moved
 * while no container is open.
 */
public final class ContainerTransferAutomation extends AutomationModule {

    /** Which way stacks travel. */
    public enum Direction {
        DEPOSIT,
        WITHDRAW
    }

    private final EnumSetting<Direction> direction =
            add("Direction", "Deposit into the container, or withdraw from it.", Direction.DEPOSIT);
    private final StringSetting filter = add(new StringSetting("Filter",
            "Item ids to move (comma separated); empty moves everything.", "", 1024));
    private final BooleanSetting protectEnchanted =
            add("ProtectEnchanted", "Never deposit enchanted, renamed or filled items.", true);
    private final BooleanSetting enabled = add("Active", "Actually perform the transfer.", false);

    public ContainerTransferAutomation() {
        super(Category.AUTOMATION, "ContainerTransfer",
                "Moves stacks between the inventory and an open container.");
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!canAct(client) || !enabled.value() || inventoryBusy()) {
            return;
        }
        ScreenHandler handler = InventoryActions.handler(client);
        if (!(handler instanceof GenericContainerScreenHandler)) {
            return;
        }
        Set<String> ids = TrashRules.parseList(filter.value());
        if (direction.value() == Direction.DEPOSIT) {
            deposit(client, handler, ids);
        } else {
            withdraw(client, handler, ids);
        }
    }

    private void deposit(MinecraftClient client, ScreenHandler handler, Set<String> ids) {
        int size = InventoryActions.containerSlots(handler);
        if (InventoryActions.containerOccupied(handler) >= size) {
            return;
        }
        for (int invSlot = 0; invSlot < com.logvex.crystal.player.InventoryUtil.MAIN_SIZE; invSlot++) {
            ItemStack stack = com.logvex.crystal.player.InventoryUtil.stackAt(client, invSlot);
            if (stack.isEmpty() || !matches(stack, ids)) {
                continue;
            }
            if (protectEnchanted.value() && TrashRules.protectedFromDrop(stack, Set.of())) {
                continue;
            }
            int screen = InventoryActions.screenSlotForPlayerSlot(client, invSlot);
            if (screen < 0) {
                continue;
            }
            int finalSlot = invSlot;
            if (submit(com.logvex.crystal.automation.inventory.InventoryTransaction
                    .builder(name(), "deposit " + stack.getName().getString())
                    .step("player " + invSlot + " -> container",
                            () -> InventoryActions.quickMove(client, screen),
                            () -> com.logvex.crystal.player.InventoryUtil.stackAt(client, finalSlot).isEmpty())
                    .build())) {
                return;
            }
        }
    }

    private void withdraw(MinecraftClient client, ScreenHandler handler, Set<String> ids) {
        int size = InventoryActions.containerSlots(handler);
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = InventoryActions.containerStack(handler, slot);
            if (stack.isEmpty() || !matches(stack, ids)) {
                continue;
            }
            final int containerSlot = slot;
            if (submit(com.logvex.crystal.automation.inventory.InventoryTransaction
                    .builder(name(), "withdraw " + stack.getName().getString())
                    .step("container " + slot + " -> player",
                            () -> InventoryActions.quickMove(client, containerSlot),
                            () -> InventoryActions.containerStack(InventoryActions.handler(client),
                                    containerSlot).isEmpty())
                    .build())) {
                return;
            }
        }
    }

    private static boolean matches(ItemStack stack, Set<String> ids) {
        return ids.isEmpty() || ids.contains(TrashRules.idOf(stack));
    }

    @Override
    public String statusLine() {
        ScreenHandler handler = InventoryActions.handler(MinecraftClient.getInstance());
        int slots = InventoryActions.containerSlots(handler);
        if (slots <= 0) {
            return "no container";
        }
        return direction.value().name().toLowerCase(java.util.Locale.ROOT)
                + ", " + InventoryActions.containerOccupied(handler) + "/" + slots;
    }
}
