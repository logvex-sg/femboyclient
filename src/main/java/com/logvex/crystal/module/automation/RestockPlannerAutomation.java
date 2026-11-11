package com.logvex.crystal.module.automation;

import com.logvex.crystal.automation.AutomationModule;
import com.logvex.crystal.automation.inventory.InventoryActions;
import com.logvex.crystal.automation.inventory.InventoryTransaction;
import com.logvex.crystal.automation.inventory.MaterialMath;
import com.logvex.crystal.automation.inventory.RestockTargets;
import com.logvex.crystal.automation.task.AutomationTask;
import com.logvex.crystal.automation.task.TaskPriority;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.player.InventoryUtil;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.StringSetting;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Item Restock Planner (automation): given a target list of items and counts, it works out what is
 * missing and then tops the hotbar up from the backpack as one queued task with progress and
 * cancellation.
 *
 * <p>The task is a real multi-tick workflow: it runs on the shared queue (so it can be paused,
 * cancelled or emergency-stopped like anything else), holds the {@code inventory} resource so it
 * cannot fight a refill, reports progress for the GUI bar, and finishes with a report of any item
 * that is simply not present anywhere - which the player must gather. It never pretends to create
 * items it does not have.
 */
public final class RestockPlannerAutomation extends AutomationModule {

    private final StringSetting targets = add(new StringSetting("Targets",
            "Item=count entries, comma or newline separated (e.g. totem=5, obsidian=64).",
            "totem=2, obsidian=64, end_crystal=64", 2048));
    private final BooleanSetting autoPlan = add("AutoPlan",
            "Plan and run a restock whenever a hotbar item drops below its target.", false);

    private int cooldown;

    public RestockPlannerAutomation() {
        super(Category.AUTOMATION, "RestockPlanner",
                "Plans and executes a restock against a target item list.");
    }

    /** The current target map. */
    public Map<String, Integer> targetMap() {
        return RestockTargets.parse(targets.value());
    }

    /** A read-only report of what is missing right now, for the GUI and commands. */
    public String report() {
        MinecraftClient client = MinecraftClient.getInstance();
        Map<String, Integer> plan = targetMap();
        if (plan.isEmpty()) {
            return "no targets configured";
        }
        if (!hasPlayer(client)) {
            return "no player";
        }
        Map<String, Integer> deficits = MaterialMath.deficits(plan, id -> countOf(client, id));
        if (deficits.isEmpty()) {
            return "all targets met";
        }
        StringBuilder builder = new StringBuilder("missing: ");
        for (Map.Entry<String, Integer> entry : deficits.entrySet()) {
            if (builder.length() > 9) {
                builder.append(", ");
            }
            builder.append(shortId(entry.getKey())).append('x').append(entry.getValue());
        }
        return builder.toString();
    }

    /**
     * Schedules a restock task on the shared queue. Returns a summary; the task itself reports
     * progress and completion.
     */
    public String planAndRun() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!canActUnscreened(client)) {
            return "cannot restock here";
        }
        Map<String, Integer> plan = targetMap();
        if (plan.isEmpty()) {
            return "no targets configured";
        }
        if (hub() == null) {
            return "automation unavailable";
        }
        List<String> order = new ArrayList<>(plan.keySet());
        int total = order.size();

        AutomationTask task = AutomationTask.builder("restock-" + System.currentTimeMillis(),
                        "Restock", (context, progress) -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (!hasPlayer(mc) || context.tickCount() < 0) {
                        return AutomationTask.StepResult.WAITING;
                    }
                    int satisfied = 0;
                    String pending = null;
                    for (String id : order) {
                        Item item = itemOf(id);
                        if (item == null) {
                            satisfied++;
                            continue;
                        }
                        int have = countOf(mc, id);
                        if (have >= plan.get(id)) {
                            satisfied++;
                            continue;
                        }
                        pending = id;
                    }
                    progress.progress(satisfied, total);
                    if (pending == null) {
                        context.notify("Restock complete", "success");
                        return AutomationTask.StepResult.COMPLETED;
                    }
                    Item item = itemOf(pending);
                    if (item == null || moveOneToHotbar(mc, item)) {
                        return AutomationTask.StepResult.RUNNING;
                    }
                    // Nothing left to move: the rest is genuinely absent and must be gathered.
                    context.notify("Restock: still missing " + shortId(pending), "warn");
                    progress.note("missing " + shortId(pending));
                    return AutomationTask.StepResult.COMPLETED;
                })
                .description("top up the hotbar to the target list")
                .owner(name())
                .priority(TaskPriority.LOW)
                .requiresResource("inventory")
                .maxAttempts(1)
                .totalSteps(total)
                .build();
        schedule(task);
        context().notifications().info("Restock planned (" + total + " target(s))");
        return "planned";
    }

    /** Moves one stack of {@code item} from the backpack into an empty hotbar slot, if possible. */
    private boolean moveOneToHotbar(MinecraftClient client, Item item) {
        if (inventoryBusy()) {
            return true;
        }
        int source = findBackpack(client, stack -> stack.isOf(item));
        if (source < 0) {
            return false;
        }
        int empty = emptyHotbarSlot(client);
        if (empty < 0) {
            return false;
        }
        final int from = source;
        final int to = empty;
        InventoryTransaction transaction = InventoryTransaction
                .builder(name(), "restock " + item.getName().getString())
                .step("backpack " + from + " -> hotbar " + to,
                        () -> InventoryActions.swapWithHotbar(client,
                                InventoryActions.screenSlotForPlayerSlot(client, from), to),
                        () -> InventoryUtil.stackAt(client, to).isOf(item))
                .build();
        submit(transaction);
        return true;
    }

    @Override
    protected void onTick() {
        if (!autoPlan.value()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (!canActUnscreened(client) || inventoryBusy()) {
            return;
        }
        if (--cooldown > 0) {
            return;
        }
        cooldown = 40;
        Map<String, Integer> plan = targetMap();
        if (plan.isEmpty()) {
            return;
        }
        Map<String, Integer> deficits = MaterialMath.deficits(plan, id -> countOf(client, id));
        if (!deficits.isEmpty()) {
            planAndRun();
        }
    }

    private static int countOf(MinecraftClient client, String id) {
        Item item = itemOf(id);
        if (item == null) {
            return 0;
        }
        int total = 0;
        for (int slot = 0; slot < InventoryUtil.MAIN_SIZE; slot++) {
            ItemStack stack = InventoryUtil.stackAt(client, slot);
            if (stack.isOf(item)) {
                total += stack.getCount();
            }
        }
        if (InventoryUtil.offhandIs(client, item)) {
            total += 1;
        }
        return total;
    }

    private static Item itemOf(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null || !Registries.ITEM.containsId(identifier)) {
            return null;
        }
        return Registries.ITEM.get(identifier);
    }

    private static String shortId(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }

    @Override
    public String statusLine() {
        return report();
    }
}
