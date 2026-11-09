package com.logvex.crystal.commandcenter.quick;

import java.util.Locale;

/**
 * What a quick action does when its trigger fires.
 *
 * <p>Every action is a named, non-arbitrary operation - the workflow can never execute a
 * script or run an arbitrary command. Some actions are informational (open a workspace, show
 * a diagnostic) and run freely; others change state (toggle a module, switch a profile) and
 * are marked {@link #mutating()} so the engine can require confirmation and an execution
 * limit before running them.
 */
public enum QuickActionType {

    /** Opens a Command Center workspace, e.g. {@code PERFORMANCE}. */
    OPEN_WORKSPACE("Open a workspace", false),
    /** Prints/raises a diagnostic message. */
    SHOW_DIAGNOSTIC("Show a diagnostic", false),
    /** Opens the ClickGUI focused on a view. */
    OPEN_CLICKGUI("Open the module GUI", false),
    /** Toggles a named module on or off. */
    TOGGLE_MODULE("Toggle a module", true),
    /** Enables a named module. */
    ENABLE_MODULE("Enable a module", true),
    /** Disables a named module. */
    DISABLE_MODULE("Disable a module", true),
    /** Switches to a named profile. */
    SWITCH_PROFILE("Switch profile", true),
    /** Runs a registered Command Center action by id. */
    RUN_ACTION("Run a Command Center action", false),
    /** Clears the Command Center's performance history. */
    CLEAR_PERFORMANCE_HISTORY("Clear performance history", false);

    private final String label;
    private final boolean mutating;

    QuickActionType(String label, boolean mutating) {
        this.label = label;
        this.mutating = mutating;
    }

    public String label() {
        return label;
    }

    /** True when the action changes state and therefore needs confirmation/limits. */
    public boolean mutating() {
        return mutating;
    }

    public static QuickActionType parse(String value) {
        if (value == null) {
            return SHOW_DIAGNOSTIC;
        }
        String needle = value.trim().toUpperCase(Locale.ROOT);
        for (QuickActionType type : values()) {
            if (type.name().equals(needle)) {
                return type;
            }
        }
        return SHOW_DIAGNOSTIC;
    }
}
