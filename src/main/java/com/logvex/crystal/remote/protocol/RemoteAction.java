package com.logvex.crystal.remote.protocol;

import java.util.Locale;

/**
 * The closed set of operations a controller may ask an agent to perform.
 *
 * <p>This is the security boundary of the whole feature: a {@link MessageType#COMMAND} carries
 * one of these by name plus a small, typed parameter map, and the agent refuses anything that
 * is not in this list. There is deliberately no generic "exec", "eval", "shell" or "write file"
 * action — remote control is limited to the documented LOGVEX CRYSTAL capabilities, and adding a
 * capability means adding a constant here with a handler on the agent side.
 *
 * <p>Each constant also declares whether it is <em>idempotent</em> (safe to retry after a
 * timeout without risking a double effect), which the controller's retry policy consults.
 */
public enum RemoteAction {

    /** Turn a named module on. Idempotent. */
    ENABLE_MODULE("enable_module", true),

    /** Turn a named module off. Idempotent. */
    DISABLE_MODULE("disable_module", true),

    /** Set a module setting to a value. Idempotent. */
    SET_SETTING("set_setting", true),

    /** Apply a named configuration profile. Idempotent. */
    APPLY_PROFILE("apply_profile", true),

    /** Change a module's keybind. Idempotent. */
    SET_KEYBIND("set_keybind", true),

    /** Produce a diagnostics report (read-only). Idempotent. */
    REQUEST_DIAGNOSTICS("request_diagnostics", true),

    /** Save the agent's configuration to disk. Idempotent. */
    SAVE_CONFIG("save_config", true),

    /** Restart the agent process where the host supports it. Not idempotent. */
    RESTART_AGENT("restart_agent", false),

    /** Ask the agent to disconnect from the controller. Idempotent. */
    DISCONNECT("disconnect", true);

    private final String wire;
    private final boolean idempotent;

    RemoteAction(String wire, boolean idempotent) {
        this.wire = wire;
        this.idempotent = idempotent;
    }

    /** The stable wire name (lower snake case), independent of the Java constant name. */
    public String wire() {
        return wire;
    }

    /** True when repeating this action has no additional effect, so it is safe to retry. */
    public boolean idempotent() {
        return idempotent;
    }

    /** The action for a wire name, or null when unknown. */
    public static RemoteAction parse(String wire) {
        if (wire == null) {
            return null;
        }
        String needle = wire.trim().toLowerCase(Locale.ROOT);
        for (RemoteAction action : values()) {
            if (action.wire.equals(needle)) {
                return action;
            }
        }
        return null;
    }
}
