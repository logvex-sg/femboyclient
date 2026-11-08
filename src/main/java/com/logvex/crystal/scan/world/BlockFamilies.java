package com.logvex.crystal.scan.world;

import java.util.Locale;

/**
 * Recognises block families from their registry id.
 *
 * <p>The scanner counts block families (logs, ores, containers, redstone, ...) rather than
 * tracking individual block types, so a new wood or ore variant added by a future Minecraft
 * version is classified without a code change. The matching is by id substring and is
 * deliberately Minecraft-free so it can be unit tested exhaustively.
 */
public final class BlockFamilies {

    private BlockFamilies() {
    }

    private static String id(String blockId) {
        return blockId == null ? "" : blockId.toLowerCase(Locale.ROOT);
    }

    public static boolean isLog(String blockId) {
        String s = id(blockId);
        return s.endsWith("_log") || s.contains("_log") || s.contains("stem") || s.contains("hyphae")
                || s.equals("oak_wood") || s.endsWith("_wood");
    }

    public static boolean isLeaves(String blockId) {
        return id(blockId).contains("leaves");
    }

    public static boolean isCrop(String blockId) {
        String s = id(blockId);
        return s.contains("wheat") || s.contains("carrot") || s.contains("potato")
                || s.contains("beetroot") || s.contains("melon_stem") || s.contains("pumpkin_stem")
                || s.contains("sugar_cane") || s.contains("nether_wart") || s.contains("cocoa")
                || s.contains("sweet_berry") || s.contains("bamboo") || s.contains("kelp")
                || s.contains("vine") || s.contains("farmland");
    }

    public static boolean isBuilding(String blockId) {
        String s = id(blockId);
        return s.contains("planks") || s.contains("brick") || s.contains("concrete")
                || s.contains("terracotta") || s.contains("glass") || s.contains("wool")
                || s.contains("sandstone") || s.contains("cobblestone") || s.contains("smooth_stone")
                || s.contains("polished") || s.contains("quartz") || s.contains("prismarine")
                || s.contains("blackstone") || s.contains("hay_block") || s.contains("scaffolding")
                || s.contains("_slab") || s.contains("_stairs") || s.contains("_wall")
                || s.contains("iron_block") || s.contains("gold_block") || s.contains("copper_block")
                || s.contains("deepslate_tile") || s.contains("deepslate_brick");
    }

    public static boolean isOre(String blockId) {
        return id(blockId).contains("ore") || id(blockId).contains("ancient_debris");
    }

    public static boolean isContainer(String blockId) {
        String s = id(blockId);
        return s.contains("chest") || s.contains("barrel") || s.contains("shulker_box")
                || s.contains("hopper") || s.contains("dispenser") || s.contains("dropper");
    }

    public static boolean isLight(String blockId) {
        String s = id(blockId);
        return s.contains("torch") || s.contains("lantern") || s.contains("glowstone")
                || s.contains("sea_lantern") || s.contains("shroomlight") || s.contains("campfire")
                || s.contains("end_rod") || s.contains("redstone_lamp") || s.contains("candle")
                || s.contains("froglight") || s.contains("glow_lichen") || s.contains("beacon");
    }

    public static boolean isRedstone(String blockId) {
        String s = id(blockId);
        return s.contains("redstone") || s.contains("repeater") || s.contains("comparator")
                || s.contains("piston") || s.contains("observer") || s.contains("lever")
                || s.contains("tripwire") || s.contains("daylight_detector") || s.contains("target")
                || s.contains("note_block") || s.contains("sculk_sensor") || s.contains("lightning_rod")
                || s.contains("rail");
    }

    public static boolean isSpawner(String blockId) {
        return id(blockId).contains("spawner");
    }

    public static boolean isPortal(String blockId) {
        String s = id(blockId);
        return s.contains("nether_portal") || s.contains("end_portal") || s.contains("end_gateway");
    }

    /**
     * Village markers. A bell is essentially unique to a village, and jigsaw/structure blocks only
     * appear where a structure was generated, so either is strong evidence of a village.
     */
    public static boolean isVillageMarker(String blockId) {
        String s = id(blockId);
        return s.equals("bell") || s.endsWith("_bell") || s.equals("jigsaw")
                || s.equals("structure_block");
    }

    /**
     * A weight for a storage block, used when scoring how strong a storage finding is. Shulker
     * boxes and chests are worth more than a furnace, matching the reference stash-finder's
     * ranking. The scanner accumulates this into a chunk's storage score rather than a bare count.
     */
    public static int storageWeight(String blockId) {
        String s = id(blockId);
        if (s.contains("shulker")) {
            return 4;
        }
        if (s.contains("chest") || s.contains("barrel")) {
            return 3;
        }
        if (s.contains("hopper") || s.contains("dispenser") || s.contains("dropper")) {
            return 2;
        }
        return 1;
    }
}
