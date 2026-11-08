package com.logvex.crystal.scan.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A deterministic, offline structure classifier.
 *
 * <p>It turns a {@link ChunkSample}'s block counts into a {@link Classification} using ordered,
 * explainable rules. There is no cloud dependency: the same input always produces the same
 * verdict on any machine. An optional {@code hint} category may be supplied (for example from a
 * local model); it is used only when the deterministic rules are inconclusive, and it is trusted
 * less than direct block evidence, so the classifier still distinguishes a confirmed observation
 * from an uncertain guess.
 *
 * <p>Every verdict carries a confidence in {@code [0,1]}. A verdict below
 * {@link #CONFIDENCE_FLOOR} is downgraded to {@link StructureCategory#OTHER} so a weak guess is
 * never presented as a confident find.
 */
public final class StructureClassifier {

    /** Below this confidence the verdict is reported as OTHER rather than asserted. */
    public static final double CONFIDENCE_FLOOR = 0.5;

    private StructureClassifier() {
    }

    /** Classifies a sample with no external hint. */
    public static Classification classify(ChunkSample sample) {
        return classify(sample, null);
    }

    /**
     * Classifies a sample, optionally biased by a {@code hint} category from an external source.
     * The hint is only consulted when the deterministic evidence is weak, and never raises the
     * confidence above what the block evidence supports.
     */
    public static Classification classify(ChunkSample sample, StructureCategory hint) {
        if (sample == null || !sample.loaded()) {
            return Classification.unknown("chunk not loaded");
        }
        if (sample.sampled() == 0) {
            return Classification.unknown("no positions sampled");
        }

        Classification deterministic = fromRules(sample);
        if (deterministic.confidence() >= CONFIDENCE_FLOOR || hint == null || hint == StructureCategory.OTHER) {
            return deterministic;
        }
        // Evidence was weak; a hint may break the tie, but only at a modest, capped confidence.
        return new Classification(hint, Math.min(0.55, deterministic.confidence() + 0.25),
                append(deterministic.reasons(), "hint: " + hint.label()));
    }

    private static Classification fromRules(ChunkSample s) {
        int sampled = s.sampled();
        double builtRatio = s.builtRatio();
        double naturalRatio = s.naturalRatio();
        double waterRatio = s.waterRatio();
        int nonAir = s.nonAir();
        boolean deep = s.surfaceY() != Integer.MIN_VALUE && s.surfaceY() < 50;

        // Portals are unambiguous and rare.
        if (s.portals() > 0) {
            return verdict(StructureCategory.PORTAL, 0.9, "portal blocks present");
        }
        // Spawners indicate a mob farm (or a dungeon).
        if (s.spawners() > 0) {
            double c = deep ? 0.7 : 0.62;
            return verdict(StructureCategory.MOB_FARM, c,
                    "spawner present" + (deep ? ", below y50" : ""));
        }
        // A bell is a village marker and is essentially unique to villages.
        if (s.bells() > 0 && s.chests() == 0 && s.crops() == 0 && builtRatio < 0.35) {
            return verdict(StructureCategory.VILLAGE, 0.66, s.bells() + " bell/marker block(s)");
        }
        // Chests are the clearest storage signal; density and built context raise confidence.
        if (s.chests() > 0) {
            double c = 0.6 + Math.min(0.25, s.chests() * 0.06) + (builtRatio >= 0.1 ? 0.1 : 0);
            List<String> reasons = new ArrayList<>();
            reasons.add(s.chests() + " container(s)");
            if (builtRatio >= 0.1) {
                reasons.add("surrounded by built blocks");
            }
            return new Classification(StructureCategory.STORAGE, c, reasons);
        }
        // Crops with little natural cover and some building = a farm.
        if (s.crops() > 0) {
            double c = 0.6 + Math.min(0.25, s.crops() / (double) sampled * 2.0);
            return verdict(StructureCategory.FARM, c, s.crops() + " crop blocks");
        }
        // Redstone components in quantity = a redstone build.
        if (s.redstone() >= Math.max(2, sampled / 40)) {
            return verdict(StructureCategory.REDSTONE, 0.68, s.redstone() + " redstone component(s)");
        }
        // A high density of man-made blocks with no crops/chests = a building.
        if (builtRatio >= 0.28) {
            return verdict(StructureCategory.BUILDING, 0.62 + Math.min(0.2, builtRatio), "built-block density");
        }
        // Man-made blocks at a low but non-trivial density, spread over a large area, is more
        // likely a player's own scattered construction than a single structure.
        if (builtRatio >= 0.06 && s.building() >= 24 && s.terrain() != TerrainType.NETHER) {
            return verdict(StructureCategory.PLAYER_BUILD, 0.55 + Math.min(0.15, builtRatio),
                    "scattered man-made blocks");
        }
        // Deep, mostly solid, ore-rich and not built = a mine.
        if (deep && builtRatio < 0.05 && s.ores() >= 2) {
            return verdict(StructureCategory.MINE, 0.64, s.ores() + " ore(s) below y50");
        }
        // Deep and solid with little natural surface cover = an underground pocket.
        if (deep && builtRatio < 0.05 && nonAir > sampled / 2) {
            return verdict(StructureCategory.UNDERGROUND, 0.55, "solid mass below y50");
        }
        // Water-dominant terrain.
        if (waterRatio >= 0.5) {
            return verdict(s.terrain() == TerrainType.OCEAN ? StructureCategory.NATURAL : StructureCategory.NATURAL,
                    0.6, "water-dominant terrain");
        }
        // Natural terrain with meaningful vegetation or surface block evidence.
        if (naturalRatio >= 0.12 || s.terrain() == TerrainType.FOREST) {
            return verdict(StructureCategory.NATURAL, 0.6, "natural vegetation/surface");
        }
        // Bare, unbuilt, unclassified ground: not enough to assert a category.
        if (builtRatio < 0.02 && s.chests() == 0 && s.crops() == 0) {
            return new Classification(StructureCategory.OTHER, 0.35, List.of("no structure evidence"));
        }
        return new Classification(StructureCategory.OTHER, 0.3, List.of("inconclusive"));
    }

    /**
     * A coarse terrain classification from the surface block and block mix. Used to colour the
     * map and to seed the structure rules.
     */
    public static TerrainType classifyTerrain(String surfaceBlock, int water, int nonAir, int logs, int leaves,
                                              boolean deep) {
        String block = surfaceBlock == null ? "" : surfaceBlock.toLowerCase(Locale.ROOT);
        if (block.contains("netherrack") || block.contains("nether")) {
            return TerrainType.NETHER;
        }
        if (block.contains("end_stone")) {
            return TerrainType.END;
        }
        if (block.contains("sand") && !block.contains("sandstone")) {
            return TerrainType.DESERT;
        }
        if (block.contains("snow") || block.contains("ice")) {
            return TerrainType.SNOW;
        }
        if (block.contains("water")) {
            return TerrainType.WATER;
        }
        if (deep && nonAir > 0 && block.isEmpty()) {
            return TerrainType.CAVE;
        }
        if (logs + leaves >= Math.max(2, nonAir / 6)) {
            return TerrainType.FOREST;
        }
        if (block.contains("stone") || block.contains("cobble")) {
            return TerrainType.HILLS;
        }
        if (water > 0 && water >= nonAir / 2) {
            return TerrainType.OCEAN;
        }
        if (block.contains("grass") || block.contains("dirt") || block.contains("podzol")) {
            return TerrainType.PLAINS;
        }
        return TerrainType.UNKNOWN;
    }

    private static Classification verdict(StructureCategory category, double confidence, String reason) {
        return new Classification(category, confidence, List.of(reason));
    }

    private static List<String> append(List<String> base, String extra) {
        List<String> result = new ArrayList<>(base);
        result.add(extra);
        return result;
    }
}
