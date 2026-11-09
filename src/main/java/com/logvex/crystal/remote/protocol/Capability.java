package com.logvex.crystal.remote.protocol;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * What one instance is able to do, discovered during the handshake and refreshed on demand.
 *
 * <p>A controller must never assume a command worked just because it was sent: an agent that
 * cannot perform an action advertises the gap here, and the controller reports that instance as
 * <em>unsupported</em> rather than success. Capabilities are also what makes a mixed-version
 * fleet safe — a 1.21.9 agent that has no world-space ESP simply does not advertise
 * {@link #WORLD_ESP}, so a bulk "enable ESP" is reported per instance instead of failing
 * silently on some and pretending on others.
 *
 * <p>Minecraft-independent value type; the client bridge maps live subsystems onto these flags.
 */
public final class Capability {

    private final Set<Flag> flags;

    public Capability(Set<Flag> flags) {
        this.flags = flags == null || flags.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(flags));
    }

    public static Capability none() {
        return new Capability(EnumSet.noneOf(Flag.class));
    }

    public static Capability of(Flag... flags) {
        EnumSet<Flag> set = EnumSet.noneOf(Flag.class);
        Collections.addAll(set, flags);
        return new Capability(set);
    }

    public boolean supports(Flag flag) {
        return flag != null && flags.contains(flag);
    }

    public Set<Flag> flags() {
        return flags;
    }

    /** The wire names of the supported flags, for the STATUS payload. */
    public java.util.List<String> wireNames() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (Flag flag : flags) {
            out.add(flag.wire());
        }
        return out;
    }

    /** Rebuilds a capability set from wire names, ignoring anything unrecognised. */
    public static Capability fromWire(java.util.List<String> names) {
        EnumSet<Flag> set = EnumSet.noneOf(Flag.class);
        if (names != null) {
            for (String name : names) {
                Flag flag = Flag.parse(name);
                if (flag != null) {
                    set.add(flag);
                }
            }
        }
        return new Capability(set);
    }

    @Override
    public String toString() {
        return "Capability" + flags;
    }

    /** The individual things an instance may or may not be able to do. */
    public enum Flag {

        /** Can enable/disable modules and change their settings. */
        MODULES("modules"),

        /** Can apply configuration profiles. */
        PROFILES("profiles"),

        /** Can change keybinds. */
        KEYBINDS("keybinds"),

        /** Can produce a diagnostics report. */
        DIAGNOSTICS("diagnostics"),

        /** Can save its configuration to disk on request. */
        SAVE_CONFIG("save_config"),

        /** The host can restart the agent process. */
        RESTART("restart"),

        /** World-space ESP exists on this Minecraft version (absent on 1.21.9). */
        WORLD_ESP("world_esp"),

        /** The agent will share the server address (subject to privacy settings). */
        SERVER_ADDRESS("server_address"),

        /** The agent will share player coordinates (opt-in). */
        COORDINATES("coordinates");

        private final String wire;

        Flag(String wire) {
            this.wire = wire;
        }

        public String wire() {
            return wire;
        }

        public static Flag parse(String wire) {
            if (wire == null) {
                return null;
            }
            String needle = wire.trim().toLowerCase(Locale.ROOT);
            for (Flag flag : values()) {
                if (flag.wire.equals(needle)) {
                    return flag;
                }
            }
            return null;
        }

    }
}
