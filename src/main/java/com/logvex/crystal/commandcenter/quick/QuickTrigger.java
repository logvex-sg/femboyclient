package com.logvex.crystal.commandcenter.quick;

import java.util.Locale;

/**
 * The explicit events a quick action can react to.
 *
 * <p>Triggers are a closed, named set - never a script - so the set of things that can start a
 * workflow is auditable and cannot grow without a code change. Each trigger is evaluated by
 * {@link QuickActionEngine} against a {@link QuickActionContext} once per tick, and a trigger
 * that reports a persistent state (rather than an event) fires on its rising edge only, so a
 * condition that stays true does not re-fire every tick.
 */
public enum QuickTrigger {

    /** Fires when any new problem appears in the status model. */
    PROBLEM_APPEARS("A problem appears", true),
    /** Fires when a specific status section develops a problem (see the action's section filter). */
    SECTION_PROBLEM("A section has a problem", true),
    /** Fires when the module error count rises. */
    MODULE_ERROR("A module reports an error", true),
    /** Fires when an optional integration is missing. */
    INTEGRATION_UNAVAILABLE("An integration is unavailable", true),
    /** Fires when the performance intelligence reports a degraded state. */
    PERFORMANCE_DEGRADED("Performance degrades", true),
    /** Fires when a crash has been recorded. */
    CRASH_RECORDED("A crash is recorded", true),
    /** Never fires on its own; only runs when the user presses its button. */
    MANUAL("Run manually", false);

    private final String label;
    private final boolean edge;

    QuickTrigger(String label, boolean edge) {
        this.label = label;
        this.edge = edge;
    }

    public String label() {
        return label;
    }

    /** True when the trigger is a state whose rising edge should fire it, not every tick it holds. */
    public boolean edge() {
        return edge;
    }

    public static QuickTrigger parse(String value) {
        if (value == null) {
            return MANUAL;
        }
        String needle = value.trim().toUpperCase(Locale.ROOT);
        for (QuickTrigger trigger : values()) {
            if (trigger.name().equals(needle)) {
                return trigger;
            }
        }
        return MANUAL;
    }
}
