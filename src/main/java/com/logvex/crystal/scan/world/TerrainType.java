package com.logvex.crystal.scan.world;

/**
 * A coarse terrain classification for a mapped chunk, derived from the block mix the client
 * already has loaded. Used to colour the dashboard map and to seed the structure classifier.
 */
public enum TerrainType {

    UNKNOWN("unknown"),
    PLAINS("plains"),
    FOREST("forest"),
    HILLS("hills"),
    MOUNTAINS("mountains"),
    DESERT("desert"),
    SNOW("snow"),
    OCEAN("ocean"),
    WATER("water"),
    CAVE("cave"),
    NETHER("nether"),
    END("end");

    private final String label;

    TerrainType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
