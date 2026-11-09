package com.logvex.crystal.variant;

/**
 * Which build of LOGVEX CRYSTAL is running.
 *
 * <p>The standard client and the Overseer client share one source tree; the difference is a
 * build-time feature manifest ({@code logvex-features.txt}) and the feature classes it names.
 * The core never references a feature class directly, so this enum is derived from what actually
 * loaded rather than from a compile-time flag.
 */
public enum Variant {

    /** The standard single-instance client. */
    STANDARD("LOGVEX CRYSTAL", "standard"),

    /** The client with the Overseer administration feature. */
    OVERSEER("LOGVEX CRYSTAL OVERSEER", "overseer");

    private final String title;
    private final String id;

    Variant(String title, String id) {
        this.title = title;
        this.id = id;
    }

    /** The display title for the window, watermark and diagnostics. */
    public String title() {
        return title;
    }

    /** The machine id used in release manifests ({@code standard} / {@code overseer}). */
    public String id() {
        return id;
    }
}
