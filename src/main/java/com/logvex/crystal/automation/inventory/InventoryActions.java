package com.logvex.crystal.automation.inventory;

import com.logvex.crystal.player.InventoryUtil;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Thin, read-only-first helpers over the <em>open</em> screen handler, so a transaction step can
 * describe a click in screen-slot terms regardless of whether the player's own inventory or a
 * container is open.
 *
 * <p>Everything here goes through {@code interactionManager.clickSlot}, the same path the vanilla
 * client uses, so the server validates each action. Nothing reads hidden server state, and no
 * method moves an item the player does not already hold. Screen-slot indices follow the vanilla
 * layout: a container's own slots come first, then the player inventory, then the hotbar.
 */
public final class InventoryActions {

    private InventoryActions() {
    }

    // ------------------------------------------------------------------ geometry

    /** The sync id of the currently open screen handler, or -1 when none is open. */
    public static int syncId(MinecraftClient client) {
        return client.player == null ? -1 : client.player.currentScreenHandler.syncId;
    }

    /** The screen handler the player currently has open (never null once a player exists). */
    public static ScreenHandler handler(MinecraftClient client) {
        return client == null || client.player == null ? null : client.player.currentScreenHandler;
    }

    /** The number of the container's own slots in the open handler (0 for the player screen). */
    public static int containerSlots(ScreenHandler handler) {
        if (handler instanceof GenericContainerScreenHandler generic) {
            return generic.getRows() * 9;
        }
        return 0;
    }

    /** The first screen slot of the player's main inventory in the open handler. */
    public static int playerInventoryStart(ScreenHandler handler) {
        return containerSlots(handler);
    }

    /** The first screen slot of the player's hotbar in the open handler. */
    public static int playerHotbarStart(ScreenHandler handler) {
        return containerSlots(handler) + 27;
    }

    /** The screen slot of a main-inventory slot (0..35) in the open handler. */
    public static int screenSlotForInventory(ScreenHandler handler, int inventorySlot) {
        return containerSlots(handler) + inventorySlot;
    }

    /** The main-inventory slot (0..35) a screen slot maps to, or -1 when it is not the player's. */
    public static int inventorySlotForScreen(ScreenHandler handler, int screenSlot) {
        int start = containerSlots(handler);
        int index = screenSlot - start;
        return index >= 0 && index < InventoryUtil.MAIN_SIZE ? index : -1;
    }

    /**
     * The screen slot of a player main-inventory slot (0..35), found by matching the slot's backing
     * inventory rather than assuming a layout. This is correct for the player screen and for every
     * container handler, including ones whose player section is not a plain 36-slot block (the
     * crafting screen, the creative screen), so a swap lands on the intended slot everywhere.
     */
    public static int screenSlotForPlayerSlot(MinecraftClient client, int inventorySlot) {
        ClientPlayerEntity player = client.player;
        if (player == null || inventorySlot < 0 || inventorySlot >= InventoryUtil.MAIN_SIZE) {
            return -1;
        }
        return findScreenSlot(player, inventorySlot);
    }

    /** The screen slot of the offhand slot in the open handler, or -1 when it is not shown. */
    public static int offhandScreenSlot(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return -1;
        }
        return findScreenSlot(player, PlayerInventory.OFF_HAND_SLOT);
    }

    private static int findScreenSlot(ClientPlayerEntity player, int inventoryIndex) {
        for (Slot slot : player.currentScreenHandler.slots) {
            if (slot.inventory == player.getInventory() && slot.getIndex() == inventoryIndex) {
                return slot.id;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ reads

    /** The stack shown in a screen slot, or an empty stack when out of range. */
    public static ItemStack screenStack(ScreenHandler handler, int screenSlot) {
        if (handler == null || screenSlot < 0 || screenSlot >= handler.slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = handler.getSlot(screenSlot);
        return slot == null ? ItemStack.EMPTY : slot.getStack();
    }

    /** The stack in a container slot (0..size-1) of the open handler, or empty. */
    public static ItemStack containerStack(ScreenHandler handler, int containerSlot) {
        if (containerSlot < 0 || containerSlot >= containerSlots(handler)) {
            return ItemStack.EMPTY;
        }
        return screenStack(handler, containerSlot);
    }

    /** The stack currently held on the cursor, or an empty stack. */
    public static ItemStack cursorStack(MinecraftClient client) {
        ScreenHandler handler = handler(client);
        return handler == null ? ItemStack.EMPTY : handler.getCursorStack();
    }

    /** The number of occupied container slots in the open handler. */
    public static int containerOccupied(ScreenHandler handler) {
        int occupied = 0;
        for (int i = 0; i < containerSlots(handler); i++) {
            if (!containerStack(handler, i).isEmpty()) {
                occupied++;
            }
        }
        return occupied;
    }

    // ------------------------------------------------------------------ actions

    /** True when a screen (container) is open, i.e. moves must address the container handler. */
    public static boolean containerOpen(MinecraftClient client) {
        return containerSlots(handler(client)) > 0;
    }

    /** Shift-clicks (quick-moves) a screen slot, the action used to move a stack wholesale. */
    public static boolean quickMove(MinecraftClient client, int screenSlot) {
        return click(client, screenSlot, 0, SlotActionType.QUICK_MOVE);
    }

    /** Picks up or places the stack on the cursor at a screen slot. */
    public static boolean pickup(MinecraftClient client, int screenSlot) {
        return click(client, screenSlot, 0, SlotActionType.PICKUP);
    }

    /** Hotbar-swap: swaps a screen slot with the given hotbar slot (button 0..8). */
    public static boolean swapWithHotbar(MinecraftClient client, int screenSlot, int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot >= PlayerInventory.getHotbarSize()) {
            return false;
        }
        return click(client, screenSlot, hotbarSlot, SlotActionType.SWAP);
    }

    /** Swaps the offhand (button 40) with a screen slot. */
    public static boolean swapWithOffhand(MinecraftClient client, int screenSlot) {
        return click(client, screenSlot, 40, SlotActionType.SWAP);
    }

    /** Throws one item from a screen slot (drop key). */
    public static boolean throwOne(MinecraftClient client, int screenSlot) {
        return click(client, screenSlot, 0, SlotActionType.THROW);
    }

    /** Throws the whole stack from a screen slot. */
    public static boolean throwStack(MinecraftClient client, int screenSlot) {
        return click(client, screenSlot, 1, SlotActionType.THROW);
    }

    private static boolean click(MinecraftClient client, int screenSlot, int button,
                                 SlotActionType type) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.interactionManager == null) {
            return false;
        }
        ScreenHandler handler = player.currentScreenHandler;
        if (screenSlot < 0 || screenSlot >= handler.slots.size()) {
            return false;
        }
        client.interactionManager.clickSlot(handler.syncId, screenSlot, button, type, player);
        return true;
    }

    /** True when {@code stack} is protected by any of the given item ids (exact item match). */
    public static boolean isProtected(ItemStack stack, Set<Item> protectedItems) {
        return !stack.isEmpty() && protectedItems.contains(stack.getItem());
    }
}
