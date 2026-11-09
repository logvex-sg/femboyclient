package com.logvex.crystal.release;

/**
 * Which release channel an artifact belongs to. A stable artifact has passed every release quality
 * gate; an experimental artifact has not and must be advertised as such. The two are never mixed:
 * the updater only offers an experimental build when the user has opted into that channel.
 */
public enum ReleaseChannel {
    STABLE("stable"),
    EXPERIMENTAL("experimental");

    private final String wire;

    ReleaseChannel(String wire) {
        this.wire = wire;
    }

    /** The lower-case name used in manifests. */
    public String wire() {
        return wire;
    }

    /** Parses a manifest value, returning {@code null} when it is not a known channel. */
    public static ReleaseChannel parse(String value) {
        if (value == null) {
            return null;
        }
        for (ReleaseChannel channel : values()) {
            if (channel.wire.equalsIgnoreCase(value.trim())) {
                return channel;
            }
        }
        return null;
    }
}
