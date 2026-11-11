package com.logvex.crystal.module.automation;

import com.logvex.crystal.automation.AutomationModule;
import com.logvex.crystal.automation.inventory.HotbarPresetStore;
import com.logvex.crystal.automation.inventory.InventoryActions;
import com.logvex.crystal.automation.inventory.InventoryTransaction;
import com.logvex.crystal.config.ConfigPaths;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.player.InventoryUtil;
import com.logvex.crystal.setting.StringSetting;
import java.io.IOException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Hotbar Presets (automation): names a set of items to specific hotbar slots and applies it as one
 * arbitrated transaction, so a preset can never interleave with a refill or a sort.
 *
 * <p>A preset only records item <em>ids</em> per slot; applying it pulls each required item out of
 * the backpack into its slot and leaves slots the preset does not mention untouched. The store is
 * persisted alongside the rest of the client's configuration.
 */
public final class HotbarPresetsAutomation extends AutomationModule {

    private final StringSetting presetName = add(new StringSetting("Preset",
            "The preset to apply with ; auto hotbar apply.", "default", 64));
    private final StringSetting capture = add(new StringSetting("Capture",
            "Item ids per hotbar slot (comma separated, blank = any) saved by 'capture'.", "", 1024));

    // Resolved lazily: ConfigPaths touches the Fabric loader, which is absent in unit tests.
    private HotbarPresetStore store;
    private boolean loaded;

    public HotbarPresetsAutomation() {
        super(Category.AUTOMATION, "HotbarPresets",
                "Applies named hotbar layouts as one transaction.");
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            store = new HotbarPresetStore(ConfigPaths.directory().resolve("hotbar-presets.json"));
            store.load();
        } catch (RuntimeException e) {
            // No Fabric loader (headless test) - keep an in-memory store so the module still works.
            store = new HotbarPresetStore(java.nio.file.Path.of("hotbar-presets.json"));
        }
    }

    /** The persisted preset store, for the GUI and commands. */
    public HotbarPresetStore store() {
        ensureLoaded();
        return store;
    }

    /** Saves the current hotbar (item ids per slot) as a named preset and persists it. */
    public String captureCurrent(String name) {
        ensureLoaded();
        MinecraftClient client = MinecraftClient.getInstance();
        if (!hasPlayer(client)) {
            return "no player";
        }
        String[] slots = new String[HotbarPresetStore.HOTBAR_SIZE];
        for (int i = 0; i < HotbarPresetStore.HOTBAR_SIZE; i++) {
            ItemStack stack = InventoryUtil.stackAt(client, i);
            if (!stack.isEmpty()) {
                slots[i] = Registries.ITEM.getId(stack.getItem()).toString();
            }
        }
        store.put(name, slots);
        save();
        return "saved preset '" + name + "' (" + store.size() + " total)";
    }

    /** Saves the comma-separated {@code capture} setting as the named preset. */
    public String captureFromSetting(String name) {
        ensureLoaded();
        String[] tokens = capture.value().split(",");
        String[] slots = new String[HotbarPresetStore.HOTBAR_SIZE];
        for (int i = 0; i < HotbarPresetStore.HOTBAR_SIZE && i < tokens.length; i++) {
            String trimmed = tokens[i].trim();
            if (!trimmed.isEmpty()) {
                slots[i] = trimmed.contains(":") ? trimmed : "minecraft:" + trimmed;
            }
        }
        store.put(name, slots);
        save();
        return "saved preset '" + name + "'";
    }

    /**
     * Applies a preset: for each slot it names, pulls the required item into that slot as one
     * transaction step. Returns a human-readable summary.
     */
    public String apply(String name) {
        ensureLoaded();
        MinecraftClient client = MinecraftClient.getInstance();
        if (!canActUnscreened(client)) {
            return "cannot apply here";
        }
        HotbarPresetStore.Preset preset = store.byName(name);
        if (preset == null) {
            return "no preset '" + name + "'";
        }
        if (inventoryBusy()) {
            return "inventory busy";
        }
        String[] slots = preset.slots();
        InventoryTransaction.Builder builder = InventoryTransaction.builder(name(), "apply hotbar preset");
        int moves = 0;
        for (int target = 0; target < HotbarPresetStore.HOTBAR_SIZE; target++) {
            String id = slots[target];
            if (id == null) {
                continue;
            }
            Item item = itemOf(id);
            if (item == null) {
                continue;
            }
            if (InventoryUtil.stackAt(client, target).isOf(item)) {
                continue;
            }
            int source = findBackpack(client, stack -> stack.isOf(item));
            if (source < 0) {
                int existingHotbar = findHotbar(client, stack -> stack.isOf(item));
                if (existingHotbar < 0) {
                    continue;
                }
                source = existingHotbar;
            }
            final int from = source;
            final int to = target;
            builder.step("slot " + from + " -> hotbar " + to,
                    () -> InventoryActions.swapWithHotbar(client,
                            InventoryActions.screenSlotForPlayerSlot(client, from), to),
                    () -> InventoryUtil.stackAt(client, to).isOf(item));
            moves++;
        }
        if (moves == 0) {
            return "preset already applied";
        }
        if (!submit(builder.build())) {
            return "could not schedule";
        }
        context().notifications().success("Applying hotbar preset '" + name + "'");
        return "applying " + moves + " slot(s)";
    }

    @Override
    protected void onTick() {
        ensureLoaded();
    }

    private static Item itemOf(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null || !Registries.ITEM.containsId(identifier)) {
            return null;
        }
        return Registries.ITEM.get(identifier);
    }

    private void save() {
        try {
            store.save();
        } catch (IOException e) {
            context().notifications().error("Could not save hotbar presets");
        }
    }

    @Override
    public String statusLine() {
        ensureLoaded();
        return store.size() + " preset(s)";
    }
}
