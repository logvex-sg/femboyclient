package com.logvex.crystal.module.automation;

import com.logvex.crystal.automation.AutomationModule;
import com.logvex.crystal.automation.inventory.InventoryActions;
import com.logvex.crystal.automation.inventory.InventoryTransaction;
import com.logvex.crystal.automation.inventory.TrashRules;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.player.InventoryUtil;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import com.logvex.crystal.setting.StringSetting;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;

/**
 * AutoTrash (automation): drops junk items the user lists, with an explicit protection list and a
 * hard safety gate.
 *
 * <p>A stack is only ever dropped when it is on the trash list <em>and</em> is not protected,
 * not enchanted, not renamed and not a filled container - see {@link TrashRules}. Only the
 * backpack is scanned, so the hotbar the player set up is never touched. Each drop is one
 * transaction through the shared arbiter.
 */
public final class AutoTrashAutomation extends AutomationModule {

    private final StringSetting trashList = add(new StringSetting("TrashItems",
            "Comma-separated item ids to drop (e.g. cobblestone, dirt).",
            "minecraft:cobblestone,minecraft:dirt,minecraft:gravel", 1024));
    private final StringSetting protectedList = add(new StringSetting("ProtectedItems",
            "Comma-separated item ids that are never dropped.",
            "minecraft:netherite_ingot,minecraft:totem_of_undying,minecraft:ender_pearl", 1024));
    private final BooleanSetting protectEnchanted =
            add("ProtectEnchanted", "Never drop an enchanted item.", true);
    private final IntSetting cooldown = add("Cooldown", "Minimum ticks between drops.", 5, 1, 40);

    private int ticks;
    private int droppedTotal;

    public AutoTrashAutomation() {
        super(Category.AUTOMATION, "AutoTrash", "Drops listed junk from the backpack, with protection.");
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
        Set<String> trash = TrashRules.parseList(trashList.value());
        Set<String> protect = TrashRules.parseList(protectedList.value());
        if (trash.isEmpty()) {
            return;
        }
        for (int slot = InventoryUtil.BACKPACK_START; slot < InventoryUtil.MAIN_SIZE; slot++) {
            ItemStack stack = InventoryUtil.stackAt(client, slot);
            if (!isTrash(stack, trash, protect)) {
                continue;
            }
            int screen = InventoryActions.screenSlotForPlayerSlot(client, slot);
            if (screen < 0) {
                continue;
            }
            final int before = stack.getCount();
            final int targetSlot = slot;
            final ItemStack target = stack;
            InventoryTransaction transaction = InventoryTransaction
                    .builder(name(), "trash " + stack.getName().getString())
                    .step("throw " + slot,
                            () -> InventoryActions.throwStack(client, screen),
                            () -> InventoryUtil.stackAt(client, targetSlot).getCount() < before
                                    || !InventoryUtil.stackAt(client, targetSlot).isOf(target.getItem()))
                    .build();
            if (submit(transaction)) {
                droppedTotal++;
                ticks = 0;
                return;
            }
        }
    }

    private boolean isTrash(ItemStack stack, Set<String> trash, Set<String> protect) {
        if (stack.isEmpty() || !trash.contains(TrashRules.idOf(stack))) {
            return false;
        }
        if (protectEnchanted.value() && stack.hasEnchantments()) {
            return false;
        }
        return !TrashRules.protectedFromDrop(stack, protect);
    }

    @Override
    public String statusLine() {
        return droppedTotal + " dropped";
    }

    /** The protected item list as a compact string, for the World Tools storage panel. */
    public String protectedSummary() {
        String raw = protectedList.value();
        if (raw == null || raw.isBlank()) {
            return "no protected items";
        }
        Set<String> ids = TrashRules.parseList(raw);
        return ids.isEmpty() ? "no protected items" : String.join(", ", ids);
    }

    /** The protected items resolved to {@link net.minecraft.item.Item}, for storage flagging. */
    public Set<net.minecraft.item.Item> protectedItems() {
        Set<net.minecraft.item.Item> items = new java.util.LinkedHashSet<>();
        for (String id : TrashRules.parseList(protectedList.value())) {
            var identifier = net.minecraft.util.Identifier.tryParse(id);
            if (identifier == null) {
                continue;
            }
            var item = net.minecraft.registry.Registries.ITEM.get(identifier);
            if (item != null && item != net.minecraft.item.Items.AIR) {
                items.add(item);
            }
        }
        return items;
    }
}
