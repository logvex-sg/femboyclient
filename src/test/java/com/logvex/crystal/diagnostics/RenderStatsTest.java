package com.logvex.crystal.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The world-render timing window. */
class RenderStatsTest {

    @Test
    void startsEmpty() {
        RenderStats stats = new RenderStats(4);
        assertEquals(0, stats.sampleCount());
        assertEquals(-1d, stats.avgCollectMillis());
        assertEquals(-1d, stats.avgRenderMillis());
        assertEquals(0, stats.lastBoxes());
        assertEquals(0, stats.lastLabels());
    }

    @Test
    void averagesTheWindow() {
        RenderStats stats = new RenderStats(4);
        stats.record(1.0, 2.0, 3, 1, 100);
        stats.record(3.0, 4.0, 5, 2, 200);
        assertEquals(2.0, stats.avgCollectMillis(), 1e-9);
        assertEquals(3.0, stats.avgRenderMillis(), 1e-9);
        assertEquals(2, stats.sampleCount());
    }

    @Test
    void lastPassCountsAreTheMostRecent() {
        RenderStats stats = new RenderStats(4);
        stats.record(1.0, 1.0, 3, 1, 100);
        stats.record(1.0, 1.0, 7, 4, 200);
        assertEquals(7, stats.lastBoxes());
        assertEquals(4, stats.lastLabels());
        assertEquals(200, stats.lastSampleMillis());
    }

    @Test
    void windowIsBoundedAndDropsTheOldest() {
        RenderStats stats = new RenderStats(2);
        stats.record(10.0, 10.0, 0, 0, 1);
        stats.record(20.0, 20.0, 0, 0, 2);
        stats.record(30.0, 30.0, 0, 0, 3);
        assertEquals(2, stats.sampleCount());
        assertEquals(25.0, stats.avgCollectMillis(), 1e-9);
    }

    @Test
    void rejectsNegativeAndNaN() {
        RenderStats stats = new RenderStats(4);
        stats.record(-1.0, 1.0, 0, 0, 1);
        stats.record(1.0, -1.0, 0, 0, 1);
        stats.record(Double.NaN, 1.0, 0, 0, 1);
        assertEquals(0, stats.sampleCount());
    }

    @Test
    void negativeCountsAreClamped() {
        RenderStats stats = new RenderStats(4);
        stats.record(1.0, 1.0, -5, -9, 1);
        assertEquals(0, stats.lastBoxes());
        assertEquals(0, stats.lastLabels());
    }

    @Test
    void resetClearsEverything() {
        RenderStats stats = new RenderStats(4);
        stats.record(5.0, 6.0, 3, 2, 100);
        stats.reset();
        assertEquals(0, stats.sampleCount());
        assertEquals(-1d, stats.avgRenderMillis());
        assertEquals(0, stats.lastBoxes());
    }

    @Test
    void reportOmitsRenderSectionWhenUnmeasured() {
        DiagnosticsService service = new DiagnosticsService(
                new com.logvex.crystal.module.ModuleManager(),
                new com.logvex.crystal.logging.ModuleErrorLog(4));
        service.setRenderStats(new RenderStats(4));
        var report = service.build(null);
        assertTrue(report.lines().stream().noneMatch(l -> l.contains("[render]")));
    }

    @Test
    void reportIncludesRenderSectionWhenMeasured() {
        DiagnosticsService service = new DiagnosticsService(
                new com.logvex.crystal.module.ModuleManager(),
                new com.logvex.crystal.logging.ModuleErrorLog(4));
        RenderStats stats = new RenderStats(4);
        stats.record(0.5, 1.5, 12, 3, 100);
        service.setRenderStats(stats);
        var report = service.build(null);
        assertTrue(report.text().contains("[render]"));
        assertTrue(report.text().contains("world esp draw: 1.50ms avg"));
        assertTrue(report.text().contains("12 box(es), 3 label(s)"));
    }
}
