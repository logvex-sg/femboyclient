package com.logvex.crystal.automation.inventory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Pure arithmetic shared by the restock planner and the build material calculator: how many more
 * of each item are still needed, and how many stacks that is.
 *
 * <p>Kept free of any client state so the counting is unit tested directly.
 */
public final class MaterialMath {

    private MaterialMath() {
    }

    /** How many more of an item are needed to reach {@code target} given {@code have} in hand. */
    public static int deficit(int target, int have) {
        return Math.max(0, target - Math.max(0, have));
    }

    /**
     * The per-item deficit for a target map, preserving the target map's iteration order so the
     * plan is deterministic. Items already satisfied are omitted.
     */
    public static Map<String, Integer> deficits(Map<String, Integer> targets, ToIntFunction<String> have) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : targets.entrySet()) {
            int missing = deficit(entry.getValue(), have.applyAsInt(entry.getKey()));
            if (missing > 0) {
                result.put(entry.getKey(), missing);
            }
        }
        return result;
    }

    /** Number of 64-item stacks needed to carry {@code count} items. */
    public static int stacksFor(int count) {
        if (count <= 0) {
            return 0;
        }
        return (count + 63) / 64;
    }

    /** Total item count of a list of stack counts, clamped at zero each. */
    public static int total(int... counts) {
        int sum = 0;
        for (int count : counts) {
            sum += Math.max(0, count);
        }
        return sum;
    }
}
