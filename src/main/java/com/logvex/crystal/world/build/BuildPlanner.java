package com.logvex.crystal.world.build;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToIntFunction;

/**
 * Turns a schematic plus the player's current inventory into the two numbers a builder needs:
 * what is still missing, and how far the build has progressed.
 *
 * <p>Pure arithmetic over the schematic's material totals - no client state - so the plan is
 * unit tested directly and the GUI can recompute it whenever the schematic changes.
 */
public final class BuildPlanner {

    private BuildPlanner() {
    }

    /**
     * The items still missing to complete {@code schematic}, given a supply function.
     * Preserves the schematic's palette order so the checklist is deterministic.
     */
    public static Map<String, Integer> missing(Schematic schematic, ToIntFunction<String> have) {
        Objects.requireNonNull(schematic, "schematic");
        Objects.requireNonNull(have, "have");
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : schematic.materialTotals().entrySet()) {
            int deficit = entry.getValue() - Math.max(0, have.applyAsInt(entry.getKey()));
            if (deficit > 0) {
                result.put(entry.getKey(), deficit);
            }
        }
        return result;
    }

    /** How many of the schematic's blocks are satisfied by the current supply. */
    public static int satisfiedBlocks(Schematic schematic, ToIntFunction<String> have) {
        int satisfied = 0;
        for (Map.Entry<String, Integer> entry : schematic.materialTotals().entrySet()) {
            satisfied += Math.min(entry.getValue(), Math.max(0, have.applyAsInt(entry.getKey())));
        }
        return satisfied;
    }

    /** Fraction of the required blocks the player already holds, in [0, 1]. */
    public static double readiness(Schematic schematic, ToIntFunction<String> have) {
        int total = schematic.blockCount();
        if (total <= 0) {
            return 1.0;
        }
        return Math.min(1.0, satisfiedBlocks(schematic, have) / (double) total);
    }

    /** The number of 64-item stacks to carry the missing materials. */
    public static int stacksMissing(Schematic schematic, ToIntFunction<String> have) {
        int stacks = 0;
        for (int missing : missing(schematic, have).values()) {
            stacks += (missing + 63) / 64;
        }
        return stacks;
    }

    /** A checklist of missing materials with a per-item "have/target" readout, for the GUI. */
    public static List<MaterialLine> checklist(Schematic schematic, ToIntFunction<String> have) {
        List<MaterialLine> lines = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : schematic.materialTotals().entrySet()) {
            int target = entry.getValue();
            int held = Math.max(0, have.applyAsInt(entry.getKey()));
            lines.add(new MaterialLine(entry.getKey(), Math.min(held, target), target,
                    Math.max(0, target - held)));
        }
        return lines;
    }

    /** One row of the material collection checklist. */
    public record MaterialLine(String itemId, int have, int target, int missing) {
        public boolean complete() {
            return missing == 0;
        }
    }
}
