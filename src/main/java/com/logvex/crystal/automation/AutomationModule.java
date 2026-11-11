package com.logvex.crystal.automation;

import com.logvex.crystal.automation.inventory.InventoryActions;
import com.logvex.crystal.automation.inventory.InventoryTransaction;
import com.logvex.crystal.automation.task.AutomationTask;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.player.InventoryUtil;
import java.util.function.Predicate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * Shared base for every automation module.
 *
 * <p>It centralises the two rules that used to be re-implemented (and sometimes got wrong) in
 * each module:
 * <ul>
 *   <li>an inventory move is only submitted through the one {@link InventoryTransactionManager},
 *       so two modules never click the same slots in the same tick; and</li>
 *   <li>a multi-step workflow is only scheduled on the one {@link AutomationService} task queue,
 *       so priorities, delays and the emergency stop apply uniformly.</li>
 * </ul>
 *
 * <p>It also exposes the guards every module needs - a live player, no open screen (for actions
 * that need the player's own inventory), and the shared "is a move already in flight" check - so
 * a module body stays about its own policy.
 */
public abstract class AutomationModule extends Module {

    protected AutomationModule(Category category, String name, String description) {
        super(category, name, description);
    }

    /** The shared hub, or null when running outside a full client (unit tests). */
    protected AutomationService hub() {
        return context() == null ? null : context().automation();
    }

    protected boolean hubAvailable() {
        return hub() != null;
    }

    /** A live player exists. */
    protected static boolean hasPlayer(MinecraftClient client) {
        return client != null && client.player != null;
    }

    /** A live player with a loaded world and an interaction manager. */
    protected static boolean canAct(MinecraftClient client) {
        return client != null && client.player != null && client.world != null
                && client.interactionManager != null;
    }

    /** A live player, world, interaction manager, and no screen open. */
    protected static boolean canActUnscreened(MinecraftClient client) {
        return canAct(client) && client.currentScreen == null;
    }

    protected static ClientPlayerEntity player(MinecraftClient client) {
        return client == null ? null : client.player;
    }

    /**
     * True when the shared inventory arbiter is currently running a move, so this module should
     * not act. When the hub is absent (unit tests) this is false.
     */
    protected boolean inventoryBusy() {
        AutomationService hub = hub();
        return hub != null && hub.transactions().busy();
    }

    /** Submits a transaction to the shared arbiter; false when the hub is absent or busy. */
    protected boolean submit(InventoryTransaction transaction) {
        AutomationService hub = hub();
        return hub != null && hub.submitTransaction(transaction);
    }

    /** Schedules a task on the shared queue; false when the hub is absent. */
    protected boolean schedule(AutomationTask task) {
        AutomationService hub = hub();
        if (hub == null) {
            return false;
        }
        hub.submitTask(task);
        return true;
    }

    /** A short line for the module's status readout. */
    public String statusLine() {
        return isEnabled() ? "enabled" : "disabled";
    }

    // ------------------------------------------------------------- shared hand helpers

    /** The first hotbar slot (0..8) whose stack matches, or -1. */
    protected static int findHotbar(MinecraftClient client, Predicate<ItemStack> match) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = InventoryUtil.stackAt(client, slot);
            if (!stack.isEmpty() && match.test(stack)) {
                return slot;
            }
        }
        return -1;
    }

    /** The first backpack slot (9..35) whose stack matches, or -1. */
    protected static int findBackpack(MinecraftClient client, Predicate<ItemStack> match) {
        for (int slot = InventoryUtil.BACKPACK_START; slot < InventoryUtil.MAIN_SIZE; slot++) {
            ItemStack stack = InventoryUtil.stackAt(client, slot);
            if (!stack.isEmpty() && match.test(stack)) {
                return slot;
            }
        }
        return -1;
    }

    /** The first empty hotbar slot that is not the selected one, or -1. */
    protected static int emptyHotbarSlot(MinecraftClient client) {
        int selected = client.player.getInventory().getSelectedSlot();
        for (int slot = 0; slot < 9; slot++) {
            if (slot != selected && InventoryUtil.stackAt(client, slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Makes the player hold a matching stack: selects a hotbar slot already holding one, or pulls
     * one from the backpack into the selected slot as a transaction. Returns true once the item is
     * (or will be) held, so callers can gate the actual use action on it.
     */
    protected boolean holdItem(MinecraftClient client, Predicate<ItemStack> match) {
        int hotbar = findHotbar(client, match);
        if (hotbar >= 0) {
            InventoryUtil.selectSlot(client, hotbar);
            return true;
        }
        return pullToHand(client, findBackpack(client, match), match);
    }

    /**
     * Pulls a specific backpack slot into the selected hotbar slot as one arbitrated transaction,
     * verified against {@code match} once the swap lands. Used when the caller has already chosen
     * the best slot (for example the most nutritious food) rather than the first match.
     */
    protected boolean pullToHand(MinecraftClient client, int sourceSlot, Predicate<ItemStack> match) {
        if (sourceSlot < InventoryUtil.BACKPACK_START || sourceSlot >= InventoryUtil.MAIN_SIZE
                || inventoryBusy()) {
            return false;
        }
        int target = client.player.getInventory().getSelectedSlot();
        int source = sourceSlot;
        InventoryTransaction transaction = InventoryTransaction.builder(name(), "pull item into hand")
                .step("backpack " + sourceSlot + " -> hotbar " + target,
                        () -> InventoryActions.swapWithHotbar(client,
                                InventoryActions.screenSlotForPlayerSlot(client, source), target),
                        () -> match.test(InventoryUtil.stackAt(client, target)))
                .build();
        return submit(transaction);
    }

    /** The backpack slot whose stack maximises {@code score}, or -1. */
    protected static int bestBackpack(MinecraftClient client, Predicate<ItemStack> match,
                                      java.util.function.ToDoubleFunction<ItemStack> score) {
        int best = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int slot = InventoryUtil.BACKPACK_START; slot < InventoryUtil.MAIN_SIZE; slot++) {
            ItemStack stack = InventoryUtil.stackAt(client, slot);
            if (stack.isEmpty() || !match.test(stack)) {
                continue;
            }
            double value = score.applyAsDouble(stack);
            if (value > bestScore) {
                bestScore = value;
                best = slot;
            }
        }
        return best;
    }

    /** The hotbar slot whose stack maximises {@code score}, or -1. */
    protected static int bestHotbar(MinecraftClient client, Predicate<ItemStack> match,
                                    java.util.function.ToDoubleFunction<ItemStack> score) {
        int best = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = InventoryUtil.stackAt(client, slot);
            if (stack.isEmpty() || !match.test(stack)) {
                continue;
            }
            double value = score.applyAsDouble(stack);
            if (value > bestScore) {
                bestScore = value;
                best = slot;
            }
        }
        return best;
    }

    /** True when the held stack matches, i.e. the use action may be performed this tick. */
    protected static boolean holding(MinecraftClient client, Predicate<ItemStack> match) {
        return match.test(InventoryUtil.heldStack(client));
    }
}
