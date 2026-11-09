package com.logvex.crystal.commandcenter.quick;

/**
 * One user-defined quick action: a trigger, an optional section filter, one action type and a
 * single argument, plus the safety limits that keep it from misbehaving.
 *
 * <p>It is deliberately data, not code. A quick action can only name one of the
 * {@link QuickActionType} operations with a string argument; it cannot run an arbitrary
 * command or expression. That constraint is what makes the feature safe to expose in a GUI:
 * the worst a malformed action can do is fail to validate.
 *
 * <p>Guards, all enforced by {@link QuickActionEngine}:
 * <ul>
 *   <li><b>Disabled by default</b> - a new action does nothing until enabled.</li>
 *   <li><b>Per-action cooldown</b> - bounded firing rate.</li>
 *   <li><b>Max executions</b> - an optional lifetime cap, so a runaway action stops itself.</li>
 *   <li><b>Confirmation</b> - a mutating action can require the user to confirm each run.</li>
 * </ul>
 */
public final class QuickAction {

    private String id;
    private String name;
    private boolean enabled;
    private QuickTrigger trigger = QuickTrigger.MANUAL;
    /** Optional section id the SECTION_PROBLEM trigger watches (empty = any section). */
    private String section = "";
    private QuickActionType type = QuickActionType.SHOW_DIAGNOSTIC;
    /** The single argument the action type needs (module name, workspace name, action id, ...). */
    private String argument = "";
    private String message = "";
    private long cooldownMillis = 30_000L;
    private int maxExecutions = 0; // 0 = unlimited
    private boolean requiresConfirmation;

    public QuickAction(String id, String name) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
    }

    public String id() {
        return id;
    }

    public QuickAction id(String value) {
        this.id = value == null ? "" : value;
        return this;
    }

    public String name() {
        return name;
    }

    public QuickAction name(String value) {
        this.name = value == null ? "" : value;
        return this;
    }

    public boolean enabled() {
        return enabled;
    }

    public QuickAction enabled(boolean value) {
        this.enabled = value;
        return this;
    }

    public QuickTrigger trigger() {
        return trigger;
    }

    public QuickAction trigger(QuickTrigger value) {
        this.trigger = value == null ? QuickTrigger.MANUAL : value;
        return this;
    }

    public String section() {
        return section;
    }

    public QuickAction section(String value) {
        this.section = value == null ? "" : value;
        return this;
    }

    public QuickActionType type() {
        return type;
    }

    public QuickAction type(QuickActionType value) {
        this.type = value == null ? QuickActionType.SHOW_DIAGNOSTIC : value;
        return this;
    }

    public String argument() {
        return argument;
    }

    public QuickAction argument(String value) {
        this.argument = value == null ? "" : value;
        return this;
    }

    public String message() {
        return message;
    }

    public QuickAction message(String value) {
        this.message = value == null ? "" : value;
        return this;
    }

    public long cooldownMillis() {
        return cooldownMillis;
    }

    public QuickAction cooldownMillis(long value) {
        this.cooldownMillis = Math.max(0L, value);
        return this;
    }

    public int maxExecutions() {
        return maxExecutions;
    }

    public QuickAction maxExecutions(int value) {
        this.maxExecutions = Math.max(0, value);
        return this;
    }

    public boolean requiresConfirmation() {
        return requiresConfirmation;
    }

    public QuickAction requiresConfirmation(boolean value) {
        this.requiresConfirmation = value;
        return this;
    }

    /** True when the action changes state and so needs confirmation or explicit opt-in. */
    public boolean mutating() {
        return type.mutating();
    }

    /** True when this action is complete enough to run: it has a name and, if needed, an argument. */
    public boolean runnable() {
        if (!enabled) {
            return false;
        }
        if (type == QuickActionType.SHOW_DIAGNOSTIC) {
            return !message.isBlank();
        }
        return !argument.isBlank();
    }

    /** A one-line description for the workspace list. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append(enabled ? "[on] " : "[off] ").append(name.isBlank() ? "(unnamed)" : name);
        sb.append(" :: ").append(trigger.label());
        if (trigger == QuickTrigger.SECTION_PROBLEM && !section.isBlank()) {
            sb.append(" (").append(section).append(')');
        }
        sb.append(" -> ").append(type.label());
        if (!argument.isBlank()) {
            sb.append(" [").append(argument).append(']');
        }
        return sb.toString();
    }

    /** A deep copy, so the editor never mutates the live rule. */
    public QuickAction copy() {
        QuickAction copy = new QuickAction(id, name);
        copy.enabled = enabled;
        copy.trigger = trigger;
        copy.section = section;
        copy.type = type;
        copy.argument = argument;
        copy.message = message;
        copy.cooldownMillis = cooldownMillis;
        copy.maxExecutions = maxExecutions;
        copy.requiresConfirmation = requiresConfirmation;
        return copy;
    }
}
