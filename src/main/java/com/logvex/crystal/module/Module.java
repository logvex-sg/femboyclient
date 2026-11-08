package com.logvex.crystal.module;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.ColorSetting;
import com.logvex.crystal.setting.DoubleSetting;
import com.logvex.crystal.setting.EnumSetting;
import com.logvex.crystal.setting.IntSetting;
import com.logvex.crystal.setting.KeybindSetting;
import com.logvex.crystal.setting.Setting;
import com.logvex.crystal.setting.SettingGroup;
import com.logvex.crystal.setting.StringSetting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Base class for every feature. A module owns its settings, its enable/disable lifecycle
 * and its dependency/conflict metadata.
 *
 * <p>Lifecycle contract:
 * <ul>
 *   <li>{@link #onEnable()} runs on the client thread after {@code enabled} flips true.</li>
 *   <li>{@link #onDisable()} runs on the client thread after {@code enabled} flips false.
 *       It must release every resource the module acquired.</li>
 *   <li>{@link #onTick()} runs once per client tick while enabled, on the client thread.</li>
 * </ul>
 *
 * <p>Modules never mutate world state directly; they go through the shared services.
 */
public abstract class Module {

    private final String name;
    private final String description;
    private final Category category;
    private final KeybindSetting keybind;

    private final List<SettingGroup> groups = new ArrayList<>();
    private final List<SettingGroup> groupsView = Collections.unmodifiableList(groups);
    private final List<String> dependencies = new ArrayList<>();
    private final List<String> conflicts = new ArrayList<>();
    private final List<com.logvex.crystal.coordination.ModuleDependency> typedDependencies = new ArrayList<>();

    private boolean enabled;
    private long enabledAtMillis;
    private long lastTickNanos;
    private String lastError;
    private int errorCount;
    private String lastThrottledKey;
    private long lastThrottledMillis;
    private boolean alwaysEnabled;
    private ModuleContext context;

    protected Module(Category category, String name, String description) {
        this.category = Objects.requireNonNull(category);
        this.name = Objects.requireNonNull(name);
        this.description = description == null ? "" : description;
        this.keybind = new KeybindSetting("Keybind", "Toggles this module.", KeybindSetting.UNBOUND, true);
    }

    /** Injected by {@link ModuleManager#attachContext} after registration, before any tick. */
    public final void attach(ModuleContext context) {
        this.context = context;
    }

    /** The shared services. Non-null once the client has finished bootstrapping. */
    protected final ModuleContext context() {
        return context;
    }

    // ---------------------------------------------------------------- identity

    public final String name() {
        return name;
    }

    public final String description() {
        return description;
    }

    public final Category category() {
        return category;
    }

    public final KeybindSetting keybind() {
        return keybind;
    }

    /** Lower-case search key used by the ClickGUI search box. */
    public final String searchKey() {
        return name.toLowerCase();
    }

    // ---------------------------------------------------------------- settings

    /** Adds an ungrouped setting. */
    protected final <S extends Setting<?>> S add(S setting) {
        return add((String) null, setting);
    }

    /** Adds an ungrouped setting under the given group name. */
    protected final <S extends Setting<?>> S add(String groupName, S setting) {
        if (groupName == null) {
            groupName = "General";
        }
        SettingGroup group = groupFor(groupName);
        group.add(setting);
        return setting;
    }

    // Convenience constructors, so a module declaration reads as one line per setting.

    protected final BooleanSetting add(String name, String description, boolean defaultValue) {
        return add(new BooleanSetting(name, description, defaultValue));
    }

    protected final IntSetting add(String name, String description, int defaultValue, int min, int max) {
        return add(new IntSetting(name, description, defaultValue, min, max));
    }

    protected final DoubleSetting add(String name, String description, double defaultValue, double min, double max) {
        return add(new DoubleSetting(name, description, defaultValue, min, max));
    }

    protected final StringSetting add(String name, String description, String defaultValue) {
        return add(new StringSetting(name, description, defaultValue, 256));
    }

    protected final ColorSetting add(String name, String description, int defaultArgb, boolean color) {
        return add(new ColorSetting(name, description, defaultArgb));
    }

    protected final <E extends Enum<E>> EnumSetting<E> add(String name, String description, E defaultValue) {
        return add(new EnumSetting<>(name, description, defaultValue));
    }

    private SettingGroup groupFor(String name) {
        for (SettingGroup group : groups) {
            if (group.name().equals(name)) {
                return group;
            }
        }
        SettingGroup created = new SettingGroup(name);
        groups.add(created);
        return created;
    }

    public final List<SettingGroup> groups() {
        return groupsView;
    }

    /** Declares a module that must be enabled before this one can be. */
    protected final void dependsOn(String moduleName) {
        dependencies.add(moduleName);
    }

    /** Declares a module that must not be enabled at the same time as this one. */
    protected final void conflictsWith(String moduleName) {
        conflicts.add(moduleName);
    }

    /**
     * Declares a typed dependency (module/service/integration/capability/mode). Unlike the
     * legacy {@link #dependsOn(String)} edge, this can express a shared service, an optional
     * integration, a runtime capability or a required mode, and it carries a human-readable
     * reason the client surfaces when the requirement is unmet. Evaluated by the coordination
     * layer; declaring one never enables anything by itself.
     */
    protected final void requires(com.logvex.crystal.coordination.ModuleDependency dependency) {
        if (dependency != null) {
            typedDependencies.add(dependency);
        }
    }

    public final List<String> dependencies() {
        return Collections.unmodifiableList(dependencies);
    }

    /** Typed dependencies declared via {@link #requires}. */
    public final List<com.logvex.crystal.coordination.ModuleDependency> typedDependencies() {
        return Collections.unmodifiableList(typedDependencies);
    }

    public final List<String> conflicts() {
        return Collections.unmodifiableList(conflicts);
    }

    /** Marks a module as always active; it cannot be toggled off and ignores its keybind. */
    protected final void alwaysEnabled() {
        this.alwaysEnabled = true;
        this.enabled = true;
    }

    public final boolean isAlwaysEnabled() {
        return alwaysEnabled;
    }

    // ---------------------------------------------------------------- lifecycle

    public final boolean isEnabled() {
        return enabled;
    }

    /** Milliseconds since this module was enabled, or 0 when disabled. */
    public final long enabledMillis() {
        return enabled ? System.currentTimeMillis() - enabledAtMillis : 0;
    }

    public final long lastTickNanos() {
        return lastTickNanos;
    }

    public final String lastError() {
        return lastError;
    }

    /** How many times this module has thrown since the client started. */
    public final int errorCount() {
        return errorCount;
    }

    /**
     * Records a failure: keeps the message for the module list, bumps the counter, logs the
     * original throwable with its stack trace and files it in the shared error log so
     * {@code ; errors} can report it. Never rethrows.
     */
    private void recordFailure(String what, Throwable t) {
        lastError = t.toString();
        errorCount++;
        CrystalClient.LOG.error("Module {} {}", name, what, t);
        CrystalClient.errors().record(name, t);
    }

    /**
     * Records a non-fatal failure from a subclass hook so it is logged with its stack trace
     * and shows up in {@code ; errors}, rather than being silently swallowed. Subclasses
     * calling this from {@link #onTick()} should rate-limit it (see {@link #reportErrorThrottled})
     * so a persistent fault cannot flood the log every tick.
     */
    protected final void reportError(String what, Throwable t) {
        recordFailure(what, t);
    }

    /**
     * Like {@link #reportError} but records at most one failure per distinct message per
     * {@code cooldownMillis}, for hooks that run every tick. Returns true when this call was
     * recorded (so the caller can also notify the user once).
     */
    protected final boolean reportErrorThrottled(String what, Throwable t, long cooldownMillis) {
        long now = System.currentTimeMillis();
        String key = what + "|" + t.getClass().getName();
        if (key.equals(lastThrottledKey) && now - lastThrottledMillis < cooldownMillis) {
            return false;
        }
        lastThrottledKey = key;
        lastThrottledMillis = now;
        recordFailure(what, t);
        return true;
    }

    /**
     * Enables the module. Returns false (without side effects) if a dependency is missing
     * or a conflict is active; the caller decides how to report that.
     */
    public final boolean enable(ModuleManager manager) {
        if (enabled) {
            return true;
        }
        String missing = manager.firstMissingDependency(this);
        if (missing != null) {
            lastError = "Missing dependency: " + missing;
            return false;
        }
        Module conflict = manager.firstActiveConflict(this);
        if (conflict != null) {
            lastError = "Conflicts with enabled module: " + conflict.name();
            return false;
        }
        enabled = true;
        enabledAtMillis = System.currentTimeMillis();
        lastError = null;
        try {
            onEnable();
        } catch (Throwable t) {
            // A module can acquire resources (listeners, workers, render hooks) before the
            // throw. Unwind with onDisable so a failed enable cannot leak them, then report.
            enabled = false;
            try {
                onDisable();
            } catch (Throwable cleanup) {
                recordFailure("failed to clean up after a failed enable", cleanup);
            }
            recordFailure("failed to enable", t);
            return false;
        }
        markConfigDirty();
        return true;
    }

    public final void disable() {
        if (!enabled || alwaysEnabled) {
            return;
        }
        enabled = false;
        try {
            onDisable();
        } catch (Throwable t) {
            recordFailure("failed to disable cleanly", t);
        }
        markConfigDirty();
    }

    /** Tells the running client that the enabled-state changed, so the autosave persists it. */
    private static void markConfigDirty() {
        CrystalClient client = CrystalClient.get();
        if (client != null) {
            client.requestSave();
        }
    }

    public final void toggle(ModuleManager manager) {
        if (enabled) {
            disable();
        } else {
            enable(manager);
        }
    }

    /** Ticks the module, isolating exceptions so one broken module cannot stall the loop. */
    public final void tickSafely() {
        if (!enabled) {
            return;
        }
        long start = System.nanoTime();
        try {
            onTick();
        } catch (Throwable t) {
            recordFailure("threw during tick", t);
        } finally {
            lastTickNanos = System.nanoTime() - start;
        }
    }

    /** Called when the client disconnects from a world; default is to disable. */
    public void onDisconnect() {
        if (enabled && !alwaysEnabled) {
            disable();
        }
    }

    /**
     * Releases resources at client shutdown. Unlike {@link #disable()} this leaves the
     * enabled flag untouched, so a module the user had on is still on next launch. Modules
     * that own threads or other native resources override {@link #onShutdown()}.
     */
    public final void shutdown() {
        try {
            onShutdown();
        } catch (Throwable t) {
            recordFailure("failed to shut down cleanly", t);
        }
    }

    // ---------------------------------------------------------------- hooks

    /** Override to release resources on client shutdown. Runs even when the module is disabled. */
    protected void onShutdown() {
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    protected void onTick() {
    }

    @Override
    public String toString() {
        return name + (enabled ? " [on]" : "");
    }
}
