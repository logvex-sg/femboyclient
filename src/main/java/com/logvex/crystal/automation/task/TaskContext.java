package com.logvex.crystal.automation.task;

import java.util.List;
import java.util.Map;

/**
 * The services and world facts a task step may read or act through, behind a narrow interface.
 *
 * <p>Keeping the queue Minecraft-independent is what lets the whole scheduling policy - delays,
 * cooldowns, priorities, conditions, resource contention, retries, progress and cancellation -
 * be unit tested with a fake context. A task can never reach the live client directly; it can
 * only call the named operations below, and every mutating call reports whether it took effect.
 *
 * <p>Named resources (locks such as {@code "inventory"}, {@code "offhand"}, {@code "container"})
 * are arbitrated by the {@link TaskQueue} itself, so a step does not touch them; it declares what
 * it needs and the queue grants it before the step runs.
 *
 * <p>All calls run on the client thread.
 */
public interface TaskContext {

    // ------------------------------------------------------------------ world facts

    /** True when a world and player are loaded. */
    boolean worldAvailable();

    /** Ticks elapsed since the queue started, for delay/cooldown maths. */
    long tickCount();

    // ------------------------------------------------------------------ effects

    /** Enables a module by name. Returns true when it is enabled afterwards. */
    boolean enableModule(String name);

    /** Disables a module by name. Returns true when it is disabled afterwards. */
    boolean disableModule(String name);

    /** Sends the player's current hotbar selection to the server. */
    boolean selectHotbar(int slot);

    /** Posts a user-facing notification. */
    void notify(String message, String level);

    /** Records a line in the local diagnostics log. */
    void log(String message);

    /** A read-only snapshot of arbitrary key/value facts a step may publish (for progress/UI). */
    Map<String, String> facts();

    /** The names of the modules the queue knows are installed. */
    List<String> installedModules();
}
