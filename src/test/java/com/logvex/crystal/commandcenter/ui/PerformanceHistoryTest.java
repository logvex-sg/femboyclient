package com.logvex.crystal.commandcenter.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.logvex.crystal.commandcenter.PerformanceSample;
import org.junit.jupiter.api.Test;

/** Covers the performance history: interval throttling, all series, and clearing. */
class PerformanceHistoryTest {

    private static PerformanceSample sample(int fps, double frameMs, int activeActions) {
        return new PerformanceSample(fps, frameMs, frameMs * 1.5, 1.0, "Render", 0.5, 2.0, 5,
                0, 0, 0, activeActions, 1000L);
    }

    @Test
    void firstSampleIsAlwaysDue() {
        PerformanceHistory history = new PerformanceHistory(8, 1000);
        assertTrue(history.sampleIfDue(1000, sample(60, 16.0, 5), 200L * 1024 * 1024));
        assertEquals(1, history.sampleCount());
    }

    @Test
    void samplesAreThrottledToTheInterval() {
        PerformanceHistory history = new PerformanceHistory(8, 1000);
        history.sampleIfDue(1000, sample(60, 16.0, 5), 100);
        assertFalse(history.sampleIfDue(1500, sample(60, 16.0, 5), 100));
        assertTrue(history.sampleIfDue(2000, sample(61, 16.4, 5), 100));
        assertEquals(2, history.sampleCount());
    }

    @Test
    void recordFeedsEverySeries() {
        PerformanceHistory history = new PerformanceHistory(8, 1000);
        history.record(sample(75, 13.3, 4), 256L * 1024 * 1024, 5000);
        assertEquals(75, history.series(PerformanceHistory.FPS).latest(), 0.0001);
        assertEquals(13.3, history.series(PerformanceHistory.FRAME_MS).latest(), 0.0001);
        assertEquals(256, history.series(PerformanceHistory.MEMORY_MB).latest(), 0.0001);
        assertEquals(4, history.series(PerformanceHistory.ACTIONS).latest(), 0.0001);
        assertEquals(6, history.all().size());
    }

    @Test
    void clearEmptiesEverySeries() {
        PerformanceHistory history = new PerformanceHistory(8, 1000);
        history.record(sample(60, 16.0, 3), 100, 1000);
        history.clear();
        assertEquals(0, history.sampleCount());
        for (MetricSeries series : history.all()) {
            assertTrue(series.isEmpty());
        }
    }
}
