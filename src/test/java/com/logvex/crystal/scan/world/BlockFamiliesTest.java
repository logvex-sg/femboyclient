package com.logvex.crystal.scan.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests block-family recognition, including a few version-agnostic cases and false positives. */
class BlockFamiliesTest {

    @Test
    void recognisesLogsAndWood() {
        assertTrue(BlockFamilies.isLog("oak_log"));
        assertTrue(BlockFamilies.isLog("crimson_stem"));
        assertTrue(BlockFamilies.isLog("stripped_birch_wood"));
        assertFalse(BlockFamilies.isLog("oak_planks"));
    }

    @Test
    void recognisesLeavesAndCrops() {
        assertTrue(BlockFamilies.isLeaves("spruce_leaves"));
        assertTrue(BlockFamilies.isCrop("wheat"));
        assertTrue(BlockFamilies.isCrop("carrots"));
        assertTrue(BlockFamilies.isCrop("farmland"));
        assertFalse(BlockFamilies.isCrop("stone"));
    }

    @Test
    void recognisesBuildingBlocks() {
        assertTrue(BlockFamilies.isBuilding("oak_planks"));
        assertTrue(BlockFamilies.isBuilding("stone_bricks"));
        assertTrue(BlockFamilies.isBuilding("white_concrete"));
        assertTrue(BlockFamilies.isBuilding("glass"));
        assertFalse(BlockFamilies.isBuilding("oak_log"));
    }

    @Test
    void recognisesContainersLightsAndRedstone() {
        assertTrue(BlockFamilies.isContainer("chest"));
        assertTrue(BlockFamilies.isContainer("trapped_chest"));
        assertTrue(BlockFamilies.isContainer("barrel"));
        assertTrue(BlockFamilies.isContainer("shulker_box"));
        assertTrue(BlockFamilies.isLight("torch"));
        assertTrue(BlockFamilies.isLight("lantern"));
        assertTrue(BlockFamilies.isRedstone("redstone_torch"));
        assertTrue(BlockFamilies.isRedstone("sticky_piston"));
        assertFalse(BlockFamilies.isContainer("stone"));
    }

    @Test
    void recognisesOresSpawnersAndPortals() {
        assertTrue(BlockFamilies.isOre("diamond_ore"));
        assertTrue(BlockFamilies.isOre("ancient_debris"));
        assertTrue(BlockFamilies.isSpawner("spawner"));
        assertTrue(BlockFamilies.isPortal("nether_portal"));
        assertTrue(BlockFamilies.isPortal("end_portal"));
    }

    @Test
    void handlesNullAndEmpty() {
        assertFalse(BlockFamilies.isLog(null));
        assertFalse(BlockFamilies.isBuilding(""));
    }
}
