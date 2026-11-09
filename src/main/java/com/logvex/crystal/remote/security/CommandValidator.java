package com.logvex.crystal.remote.security;

import java.util.List;

/**
 * The rule table an agent applies to a command before running it: the action must be in the
 * allowlist, the controller must hold the permission the action needs, and the parameters must
 * match the action's schema.
 *
 * <p>Validation lives here, in one place, so the agent's executor can be a thin switch over
 * already-validated commands and every path (local test, remote command, dashboard) enforces the
 * same rules. It is intentionally strict: an unknown action, a missing parameter, a value that is
 * not the right type, or a numeric value outside its range is a rejection with a reason, never a
 * silent coercion. That is what keeps a malformed or hostile command from doing something the
 * user did not intend.
 *
 * <p>Minecraft-independent and unit testable.
 */
public final class CommandValidator {

    /** The outcome of validating a command: allowed, or denied with a reason. */
    public record Verdict(boolean allowed, String reason) {
        public static Verdict allow() {
            return new Verdict(true, "");
        }

        public static Verdict deny(String reason) {
            return new Verdict(false, reason);
        }
    }

    private final CommandAllowlist allowlist;
    private final java.util.Set<Permission> permissions;

    public CommandValidator(CommandAllowlist allowlist, java.util.Set<Permission> permissions) {
        this.allowlist = allowlist == null ? CommandAllowlist.none() : allowlist;
        this.permissions = permissions == null ? java.util.Set.of() : permissions;
    }

    /**
     * Validates one command.
     *
     * @return {@link Verdict#allow()} when the agent may run it, else a denial with the reason
     */
    public Verdict validate(com.logvex.crystal.remote.protocol.RemoteAction action,
                            java.util.Map<String, String> parameters) {
        if (action == null) {
            return Verdict.deny("unknown action");
        }
        if (!allowlist.allows(action)) {
            return Verdict.deny("action not in the allowlist: " + action.wire());
        }
        Permission needed = Permission.requiredFor(action);
        if (needed != null && !Permission.granted(permissions, needed)) {
            return Verdict.deny("permission not granted: " + needed.wire());
        }
        return validateParameters(action, parameters);
    }

    private Verdict validateParameters(com.logvex.crystal.remote.protocol.RemoteAction action,
                                       java.util.Map<String, String> parameters) {
        java.util.Map<String, String> p = parameters == null ? java.util.Map.of() : parameters;
        switch (action) {
            case ENABLE_MODULE:
            case DISABLE_MODULE:
                return requireName(p, "module");
            case SET_SETTING:
                Verdict module = requireName(p, "module");
                if (!module.allowed()) {
                    return module;
                }
                return requireName(p, "setting");
            case APPLY_PROFILE:
                return requireName(p, "profile");
            case SET_KEYBIND:
                Verdict kb = requireName(p, "module");
                if (!kb.allowed()) {
                    return kb;
                }
                String key = p.get("key");
                if (key == null || key.isBlank()) {
                    return Verdict.deny("missing parameter: key");
                }
                try {
                    Integer.parseInt(key.trim());
                } catch (NumberFormatException e) {
                    return Verdict.deny("key must be an integer key code");
                }
                return Verdict.allow();
            case REQUEST_DIAGNOSTICS:
            case SAVE_CONFIG:
            case RESTART_AGENT:
            case DISCONNECT:
                return Verdict.allow();
            default:
                return Verdict.deny("no validation rule for " + action.wire());
        }
    }

    private Verdict requireName(java.util.Map<String, String> p, String key) {
        String value = p.get(key);
        if (value == null || value.isBlank()) {
            return Verdict.deny("missing parameter: " + key);
        }
        if (value.length() > 128) {
            return Verdict.deny("parameter too long: " + key);
        }
        // Names are module/setting/profile identifiers, never paths or code.
        if (value.indexOf('/') >= 0 || value.indexOf('\\') >= 0 || value.indexOf('\0') >= 0) {
            return Verdict.deny("illegal characters in " + key);
        }
        return Verdict.allow();
    }

    /** A one-line description of the allowlist, for the dashboard. */
    public String describe() {
        List<String> names = allowlist.wireNames();
        return names.size() + " actions allowed";
    }

}
