package com.logvex.crystal.automation.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Covers parsing a restock target list, including malformed entries. */
class RestockTargetsTest {

    @Test
    void parsesCommaAndNewlineSeparatedEntries() {
        Map<String, Integer> targets = RestockTargets.parse("totem=5, obsidian=64\nend_crystal=128");
        assertEquals(3, targets.size());
        assertEquals(5, targets.get("minecraft:totem"));
        assertEquals(64, targets.get("minecraft:obsidian"));
        assertEquals(128, targets.get("minecraft:end_crystal"));
    }

    @Test
    void keepsFullyQualifiedIds() {
        Map<String, Integer> targets = RestockTargets.parse("minecraft:diamond=9");
        assertEquals(9, targets.get("minecraft:diamond"));
    }

    @Test
    void skipsMalformedEntriesWithoutDiscardingTheRest() {
        Map<String, Integer> targets = RestockTargets.parse("totem=5, garbage, =7, x=, obsidian=3, y=abc");
        assertEquals(2, targets.size());
        assertEquals(5, targets.get("minecraft:totem"));
        assertEquals(3, targets.get("minecraft:obsidian"));
    }

    @Test
    void ignoresNonPositiveCountsAndBlankInput() {
        assertTrue(RestockTargets.parse(null).isEmpty());
        assertTrue(RestockTargets.parse("").isEmpty());
        assertTrue(RestockTargets.parse("totem=0, obsidian=-3").isEmpty());
    }

    @Test
    void formatRoundTripsThroughParse() {
        Map<String, Integer> parsed = RestockTargets.parse("totem=5, obsidian=64");
        Map<String, Integer> again = RestockTargets.parse(RestockTargets.format(parsed));
        assertEquals(parsed, again);
    }
}
