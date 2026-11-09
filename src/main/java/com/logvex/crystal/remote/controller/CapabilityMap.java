package com.logvex.crystal.remote.controller;

import com.logvex.crystal.remote.protocol.RemoteAction;

/**
 * Which {@link com.logvex.crystal.remote.protocol.Capability.Flag} an action needs, and how a
 * missing capability is reported.
 *
 * <p>Centralising the mapping means the controller, the agent and the dashboard all agree on what
 * "supports enabling modules" means, and adding an action without a capability is a compile-time
 * reminder rather than a silent gap. An action that needs no capability (disconnect) is always
 * allowed.
 *
 * <p>Minecraft-independent.
 */
public final class CapabilityMap {

    private CapabilityMap() {
    }

    /** The capability an action requires, or null when it requires none. */
    public static com.logvex.crystal.remote.protocol.Capability.Flag requiredFor(RemoteAction action) {
        if (action == null) {
            return null;
        }
        return switch (action) {
            case ENABLE_MODULE, DISABLE_MODULE, SET_SETTING ->
                    com.logvex.crystal.remote.protocol.Capability.Flag.MODULES;
            case APPLY_PROFILE -> com.logvex.crystal.remote.protocol.Capability.Flag.PROFILES;
            case SET_KEYBIND -> com.logvex.crystal.remote.protocol.Capability.Flag.KEYBINDS;
            case REQUEST_DIAGNOSTICS -> com.logvex.crystal.remote.protocol.Capability.Flag.DIAGNOSTICS;
            case SAVE_CONFIG -> com.logvex.crystal.remote.protocol.Capability.Flag.SAVE_CONFIG;
            case RESTART_AGENT -> com.logvex.crystal.remote.protocol.Capability.Flag.RESTART;
            case DISCONNECT -> null;
        };
    }

    /** True when a snapshot advertises the capability the action needs. */
    public static boolean supports(com.logvex.crystal.remote.protocol.InstanceSnapshot snapshot,
                                   RemoteAction action) {
        com.logvex.crystal.remote.protocol.Capability.Flag flag = requiredFor(action);
        if (flag == null) {
            return true;
        }
        if (snapshot == null) {
            return false;
        }
        return snapshot.capabilities().supports(flag);
    }
}
