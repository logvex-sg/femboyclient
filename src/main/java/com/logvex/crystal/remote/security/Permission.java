package com.logvex.crystal.remote.security;

import java.util.Locale;

/**
 * A single permission bit an agent may grant to a controller.
 *
 * <p>Permissions are checked <em>on the agent</em> for every command, before the action runs;
 * the controller's copy is only a convenience for greying out buttons. A permission is coarse
 * (a family of actions) while the {@link CommandAllowlist} is fine (a specific action), so an
 * agent can say "this controller may read status and toggle modules, but may not restart me".
 *
 * <p>{@link #ADMIN} implies every other permission; it is granted only to a controller the user
 * explicitly trusts, and never by default.
 */
public enum Permission {

    /** See the instance at all: status, heartbeat, module list. Always granted when paired. */
    VIEW_STATUS("view_status"),

    /** Enable/disable modules and change their settings. */
    CONTROL_MODULES("control_modules"),

    /** Apply configuration profiles. */
    APPLY_PROFILES("apply_profiles"),

    /** Change keybinds. */
    EDIT_KEYBINDS("edit_keybinds"),

    /** Request diagnostics reports. */
    READ_DIAGNOSTICS("read_diagnostics"),

    /** Ask the agent to save its configuration to disk. */
    WRITE_CONFIG("write_config"),

    /** Ask the agent to restart its process. */
    RESTART("restart"),

    /** Disconnect the agent. */
    DISCONNECT("disconnect"),

    /** Everything. Implied set is computed by {@link #effective(java.util.Set)}. */
    ADMIN("admin");

    private final String wire;

    Permission(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    /**
     * Expands a granted set so {@link #ADMIN} implies all others. Used when an agent decides
     * what a controller may do.
     */
    public static java.util.Set<Permission> effective(java.util.Set<Permission> granted) {
        java.util.EnumSet<Permission> set = java.util.EnumSet.noneOf(Permission.class);
        if (granted != null) {
            set.addAll(granted);
        }
        if (set.contains(ADMIN)) {
            set.addAll(java.util.EnumSet.allOf(Permission.class));
        }
        return set;
    }

    /** True when {@code granted} (after expansion) contains this permission. */
    public static boolean granted(java.util.Set<Permission> granted, Permission needed) {
        if (needed == null) {
            return false;
        }
        return effective(granted).contains(needed);
    }

    public static Permission parse(String wire) {
        if (wire == null) {
            return null;
        }
        String needle = wire.trim().toLowerCase(Locale.ROOT);
        for (Permission permission : values()) {
            if (permission.wire.equals(needle)) {
                return permission;
            }
        }
        return null;
    }

    /** The permission an action needs, or null when the action has no dedicated permission. */
    public static Permission requiredFor(com.logvex.crystal.remote.protocol.RemoteAction action) {
        if (action == null) {
            return null;
        }
        return switch (action) {
            case ENABLE_MODULE, DISABLE_MODULE, SET_SETTING -> CONTROL_MODULES;
            case APPLY_PROFILE -> APPLY_PROFILES;
            case SET_KEYBIND -> EDIT_KEYBINDS;
            case REQUEST_DIAGNOSTICS -> READ_DIAGNOSTICS;
            case SAVE_CONFIG -> WRITE_CONFIG;
            case RESTART_AGENT -> RESTART;
            case DISCONNECT -> DISCONNECT;
        };
    }
}
