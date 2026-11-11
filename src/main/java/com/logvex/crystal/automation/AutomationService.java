package com.logvex.crystal.automation;

import com.logvex.crystal.automation.inventory.InventoryTransaction;
import com.logvex.crystal.automation.inventory.InventoryTransactionManager;
import com.logvex.crystal.automation.task.AutomationTask;
import com.logvex.crystal.automation.task.TaskContext;
import com.logvex.crystal.automation.task.TaskQueue;
import com.logvex.crystal.logging.ModuleErrorLog;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.module.ModuleManager;
import com.logvex.crystal.notification.NotificationLevel;
import com.logvex.crystal.notification.NotificationManager;
import com.logvex.crystal.player.InventoryUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;

/**
 * The live automation hub: it owns the shared {@link TaskQueue} and
 * {@link InventoryTransactionManager}, binds them to the running client as a {@link TaskContext},
 * and is the single entry point every automation module uses to schedule work.
 *
 * <p>Two kinds of work share this one hub so they can never fight:
 * <ul>
 *   <li><b>Tasks</b> ({@link TaskQueue}) - multi-tick workflows with priorities, delays,
 *       conditions, resources, retries and progress (restock planning, sorting, container
 *       transfer, schematic building).</li>
 *   <li><b>Transactions</b> ({@link InventoryTransactionManager}) - the actual slot clicks,
 *       serialised so at most one leaves the client per tick.</li>
 * </ul>
 *
 * <p>The whole hub can be paused, resumed or emergency-stopped in one call, which is what the
 * Automation GUI and the {@code ; auto} command drive. Everything runs on the client thread.
 */
public final class AutomationService implements TaskContext {

    private final TaskQueue queue = new TaskQueue();
    private final InventoryTransactionManager transactions = new InventoryTransactionManager();
    private final ModuleManager modules;
    private final NotificationManager notifications;
    private final ModuleErrorLog errors;
    private final java.util.Map<String, String> facts = new java.util.LinkedHashMap<>();

    private long tick;
    private int submittedTasks;
    private int submittedTransactions;

    public AutomationService(ModuleManager modules, NotificationManager notifications, ModuleErrorLog errors) {
        this.modules = modules;
        this.notifications = notifications;
        this.errors = errors;
    }

    public TaskQueue queue() {
        return queue;
    }

    public InventoryTransactionManager transactions() {
        return transactions;
    }

    /**
     * Advances the hub one client tick: the transaction manager first (so a queued click lands
     * before any module reacts to the new inventory), then the task queue.
     */
    public void tick(MinecraftClient client) {
        tick++;
        long nowMillis = System.currentTimeMillis();
        transactions.tick(nowMillis);
        queue.tick(this, tick);
    }

    // ------------------------------------------------------------------ submission helpers

    /** Submits a task, returning its entry when accepted (see {@link TaskQueue#submit}). */
    public Optional<TaskQueue.Entry> submitTask(AutomationTask task) {
        Optional<TaskQueue.Entry> entry = queue.submit(task, tick);
        if (entry.isPresent()) {
            submittedTasks++;
        }
        return entry;
    }

    /** Submits an inventory transaction, serialised behind any other in-flight move. */
    public boolean submitTransaction(InventoryTransaction transaction) {
        boolean accepted = transactions.submit(transaction);
        if (accepted) {
            submittedTransactions++;
        }
        return accepted;
    }

    // ------------------------------------------------------------------ controls

    /** Pauses or resumes all automation. */
    public void setPaused(boolean paused) {
        queue.setRunning(!paused);
        if (!paused) {
            transactions.resume();
        }
    }

    public boolean isPaused() {
        return !queue.isRunning();
    }

    /** The emergency stop: cancels everything in flight and blocks further work. */
    public int emergencyStop() {
        long now = System.currentTimeMillis();
        int cancelled = queue.emergencyStop(tick, "emergency stop");
        cancelled += transactions.cancelAll(now, "emergency stop");
        return cancelled;
    }

    /** Clears an emergency stop and resumes normal scheduling. */
    public void clearEmergencyStop() {
        queue.reset();
        transactions.resume();
    }

    public boolean emergencyStopped() {
        return queue.emergencyStopped();
    }

    /** Cancels everything owned by one module. */
    public int cancelOwner(String owner) {
        long now = System.currentTimeMillis();
        int cancelled = queue.cancelOwner(owner, tick, "cancelled by owner");
        cancelled += transactions.cancelOwner(owner, now);
        return cancelled;
    }

    public int submittedTasks() {
        return submittedTasks;
    }

    public int submittedTransactions() {
        return submittedTransactions;
    }

    // ------------------------------------------------------------------ TaskContext

    @Override
    public boolean worldAvailable() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.world != null && client.player != null;
    }

    @Override
    public long tickCount() {
        return tick;
    }

    @Override
    public boolean enableModule(String name) {
        if (modules == null) {
            return false;
        }
        Module module = modules.byName(name);
        if (module == null) {
            return false;
        }
        return module.isEnabled() || module.enable(modules);
    }

    @Override
    public boolean disableModule(String name) {
        if (modules == null) {
            return false;
        }
        Module module = modules.byName(name);
        if (module == null) {
            return false;
        }
        if (module.isEnabled()) {
            module.disable();
        }
        return !module.isEnabled();
    }

    @Override
    public boolean selectHotbar(int slot) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        InventoryUtil.selectSlot(client, slot);
        return true;
    }

    @Override
    public void notify(String message, String level) {
        if (notifications != null) {
            notifications.notify(message, parseLevel(level));
        }
    }

    @Override
    public void log(String message) {
        if (errors != null) {
            errors.record("Automation", message);
        }
    }

    @Override
    public Map<String, String> facts() {
        return facts;
    }

    @Override
    public List<String> installedModules() {
        if (modules == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (Module module : modules.all()) {
            names.add(module.name());
        }
        return names;
    }

    /** Publishes a fact a step wants the GUI to show (e.g. the restock plan's target). */
    public void publishFact(String key, String value) {
        if (key == null) {
            return;
        }
        if (value == null) {
            facts.remove(key);
        } else {
            facts.put(key, value);
        }
    }

    /** A one-line summary for diagnostics and the status indicator. */
    public String summary() {
        return queue.summary() + " | " + transactions.summary();
    }

    private static NotificationLevel parseLevel(String level) {
        if (level == null) {
            return NotificationLevel.INFO;
        }
        return switch (level.toLowerCase(java.util.Locale.ROOT)) {
            case "success" -> NotificationLevel.SUCCESS;
            case "warn", "warning" -> NotificationLevel.WARN;
            case "error" -> NotificationLevel.ERROR;
            case "cute" -> NotificationLevel.CUTE;
            default -> NotificationLevel.INFO;
        };
    }
}
