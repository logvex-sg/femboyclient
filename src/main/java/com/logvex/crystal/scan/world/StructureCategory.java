package com.logvex.crystal.scan.world;

/**
 * The structure categories the classifier can assign to an observed chunk.
 *
 * <p>These are the categories the scanner is asked to distinguish. {@link #OTHER} is the
 * explicit "I could not tell" bucket: the classifier never guesses a confident category when
 * the evidence is weak, so an unclassifiable chunk lands here rather than being mislabelled.
 */
public enum StructureCategory {

    NATURAL("natural"),
    BUILDING("building"),
    STORAGE("storage"),
    FARM("farm"),
    REDSTONE("redstone"),
    UNDERGROUND("underground"),
    ABANDONED("abandoned"),
    VILLAGE("village"),
    PLAYER_BUILD("player build"),
    MOB_FARM("mob farm"),
    MINE("mine"),
    PORTAL("portal"),
    OTHER("other");

    /** True for categories that are player-made rather than world-generated. */
    public boolean playerMade() {
        return this == BUILDING || this == STORAGE || this == FARM || this == REDSTONE
                || this == MOB_FARM || this == PLAYER_BUILD;
    }

    private final String label;

    StructureCategory(String label) {
        this.label = label;
    }

    /** Human-readable label used by the HUD, dashboard and reports. */
    public String label() {
        return label;
    }
}
