package com.logvex.crystal.automation.inventory;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Parses a restock target list - {@code item=count} entries separated by commas or newlines - into
 * an ordered item-id to target-count map.
 *
 * <p>Kept Minecraft-free so the parsing (the part most likely to be mistyped) is unit tested
 * directly. A malformed entry is skipped rather than aborting the whole list, so one typo cannot
 * silently discard the rest of a plan.
 */
public final class RestockTargets {

    private RestockTargets() {
    }

    /** Parses {@code "totem=5, obsidian=64\ncrystal=128"}. */
    public static Map<String, Integer> parse(String raw) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (raw == null) {
            return result;
        }
        for (String line : raw.split("[,\\n]")) {
            String entry = line.trim();
            if (entry.isEmpty()) {
                continue;
            }
            int separator = entry.indexOf('=');
            if (separator < 0) {
                separator = entry.indexOf(':');
            }
            if (separator <= 0 || separator >= entry.length() - 1) {
                continue;
            }
            String id = entry.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            if (id.isEmpty()) {
                continue;
            }
            if (!id.contains(":")) {
                id = "minecraft:" + id;
            }
            try {
                int count = Integer.parseInt(entry.substring(separator + 1).trim());
                if (count > 0) {
                    result.put(id, count);
                }
            } catch (NumberFormatException ignored) {
                // A bad count skips this entry only.
            }
        }
        return result;
    }

    /** A stable string form for round-tripping through a setting. */
    public static String format(Map<String, Integer> targets) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Integer> entry : targets.entrySet()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }
}
