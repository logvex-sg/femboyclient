package com.logvex.crystal.release;

/**
 * Which build of LOGVEX CRYSTAL an artifact is. The two editions have separate artifacts and
 * separate release channels; the updater must never offer one edition's artifact to the other, so
 * the edition is part of every release manifest and is checked before anything is downloaded.
 */
public enum Edition {
    STANDARD("standard", "LOGVEX CRYSTAL", "logvex-crystal"),
    OVERSEER("overseer", "LOGVEX CRYSTAL OVERSEER", "logvex-crystal-overseer");

    private final String wire;
    private final String displayName;
    private final String archivePrefix;

    Edition(String wire, String displayName, String archivePrefix) {
        this.wire = wire;
        this.displayName = displayName;
        this.archivePrefix = archivePrefix;
    }

    /** The lower-case name used in manifests and build metadata. */
    public String wire() {
        return wire;
    }

    /** The human-readable product name. */
    public String displayName() {
        return displayName;
    }

    /** The prefix the edition's jar file name starts with. */
    public String archivePrefix() {
        return archivePrefix;
    }

    /** Parses a manifest value, returning {@code null} when it is not a known edition. */
    public static Edition parse(String value) {
        if (value == null) {
            return null;
        }
        for (Edition edition : values()) {
            if (edition.wire.equalsIgnoreCase(value.trim())) {
                return edition;
            }
        }
        return null;
    }
}
