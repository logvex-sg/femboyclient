package com.logvex.crystal.automation.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Covers the shared restock/material arithmetic. */
class MaterialMathTest {

    @Test
    void deficitNeverGoesNegative() {
        assertEquals(0, MaterialMath.deficit(10, 10));
        assertEquals(0, MaterialMath.deficit(10, 25));
        assertEquals(5, MaterialMath.deficit(10, 5));
        assertEquals(10, MaterialMath.deficit(10, 0));
        // A negative "have" (defensive) is treated as zero.
        assertEquals(10, MaterialMath.deficit(10, -4));
    }

    @Test
    void deficitsPreserveOrderAndOmitSatisfiedItems() {
        Map<String, Integer> targets = new LinkedHashMap<>();
        targets.put("a", 64);
        targets.put("b", 32);
        targets.put("c", 1);
        Map<String, Integer> have = Map.of("a", 64, "b", 10, "c", 0);
        Map<String, Integer> deficits = MaterialMath.deficits(targets, have::get);
        assertEquals(2, deficits.size());
        assertTrue(deficits.containsKey("b"));
        assertEquals(22, deficits.get("b"));
        assertEquals(1, deficits.get("c"));
    }

    @Test
    void stacksRoundUpToSixtyFour() {
        assertEquals(0, MaterialMath.stacksFor(0));
        assertEquals(1, MaterialMath.stacksFor(1));
        assertEquals(1, MaterialMath.stacksFor(64));
        assertEquals(2, MaterialMath.stacksFor(65));
        assertEquals(2, MaterialMath.stacksFor(128));
        assertEquals(3, MaterialMath.stacksFor(129));
    }

    @Test
    void totalClampsNegatives() {
        assertEquals(10, MaterialMath.total(4, 6));
        assertEquals(6, MaterialMath.total(-4, 6));
        assertEquals(0, MaterialMath.total(-1, -1));
    }
}
