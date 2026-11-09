package com.logvex.crystal.remote.security;

import java.util.EnumSet;
import java.util.Set;

/**
 * The set of actions an agent will run for a controller.
 *
 * <p>Permissions answer "may this controller do this family of things"; the allowlist answers
 * "which exact operations are enabled at all on this instance". Both must pass. The allowlist is
 * what stops a newly-added action from becoming remotely invocable just because it exists: an
 * agent starts from {@link #safeDefaults()} (read-only plus module toggles) and the user opts
 * into the more invasive actions (restart, config writes) explicitly.
 *
 * <p>The special value {@link RemoteAction#DISCONNECT} is always allowed, because a controller
 * that has been paired must always be able to hand the instance back.
 *
 * <p>Minecraft-independent and unit testable.
 */
public final class CommandAllowlist {

    private final Set<com.logvex.crystal.remote.protocol.RemoteAction> allowed;

    public CommandAllowlist(Set<com.logvex.crystal.remote.protocol.RemoteAction> allowed) {
        EnumSet<com.logvex.crystal.remote.protocol.RemoteAction> set =
                EnumSet.noneOf(com.logvex.crystal.remote.protocol.RemoteAction.class);
        if (allowed != null) {
            set.addAll(allowed);
        }
        set.add(com.logvex.crystal.remote.protocol.RemoteAction.DISCONNECT);
        this.allowed = java.util.Collections.unmodifiableSet(set);
    }

    /** Read-only status plus module control and diagnostics; no restart, no config writes. */
    public static CommandAllowlist safeDefaults() {
        EnumSet<com.logvex.crystal.remote.protocol.RemoteAction> set =
                EnumSet.noneOf(com.logvex.crystal.remote.protocol.RemoteAction.class);
        set.add(com.logvex.crystal.remote.protocol.RemoteAction.ENABLE_MODULE);
        set.add(com.logvex.crystal.remote.protocol.RemoteAction.DISABLE_MODULE);
        set.add(com.logvex.crystal.remote.protocol.RemoteAction.SET_SETTING);
        set.add(com.logvex.crystal.remote.protocol.RemoteAction.APPLY_PROFILE);
        set.add(com.logvex.crystal.remote.protocol.RemoteAction.SET_KEYBIND);
        set.add(com.logvex.crystal.remote.protocol.RemoteAction.REQUEST_DIAGNOSTICS);
        set.add(com.logvex.crystal.remote.protocol.RemoteAction.DISCONNECT);
        return new CommandAllowlist(set);
    }

    /** Everything the protocol defines. */
    public static CommandAllowlist everything() {
        return new CommandAllowlist(EnumSet.allOf(com.logvex.crystal.remote.protocol.RemoteAction.class));
    }

    /** Nothing but the always-allowed disconnect. */
    public static CommandAllowlist none() {
        return new CommandAllowlist(EnumSet.noneOf(com.logvex.crystal.remote.protocol.RemoteAction.class));
    }

    public boolean allows(com.logvex.crystal.remote.protocol.RemoteAction action) {
        return action != null && allowed.contains(action);
    }

    public Set<com.logvex.crystal.remote.protocol.RemoteAction> actions() {
        return allowed;
    }

    public java.util.List<String> wireNames() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (com.logvex.crystal.remote.protocol.RemoteAction action : allowed) {
            out.add(action.wire());
        }
        return out;
    }

    public static CommandAllowlist fromWire(java.util.List<String> names) {
        EnumSet<com.logvex.crystal.remote.protocol.RemoteAction> set =
                EnumSet.noneOf(com.logvex.crystal.remote.protocol.RemoteAction.class);
        if (names != null) {
            for (String name : names) {
                com.logvex.crystal.remote.protocol.RemoteAction action =
                        com.logvex.crystal.remote.protocol.RemoteAction.parse(name);
                if (action != null) {
                    set.add(action);
                }
            }
        }
        return new CommandAllowlist(set);
    }

    /** A human summary for the dashboard, e.g. {@code "7 actions"}. */
    public String describe() {
        return allowed.size() + " action" + (allowed.size() == 1 ? "" : "s");
    }

}
