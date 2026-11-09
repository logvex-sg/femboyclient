package com.logvex.crystal.commandcenter.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Covers the bounded metric ring: capacity, ordering, statistics and bad-value rejection. */
class MetricSeriesTest {

    @Test
    void emptySeriesHasNoSamples() {
        MetricSeries series = new MetricSeries("fps", "FPS", "", 8, 1000);
        assertTrue(series.isEmpty());
        assertEquals(0, series.size());
        assertTrue(series.snapshot().isEmpty());
        assertTrue(Double.isNaN(series.latest()));
    }

    @Test
    void ringStaysBoundedAndKeepsTheNewestSamples() {
        MetricSeries series = new MetricSeries("fps", "FPS", "", 4, 1000);
        for (int i = 1; i <= 10; i++) {
            series.add(i, i * 1000L);
        }
        assertEquals(4, series.size());
        assertEquals(4, series.capacity());
        MetricSeries.Snapshot snapshot = series.snapshot();
        assertEquals(4, snapshot.values().length);
        // Oldest first: the last four adds were 7,8,9,10.
        assertEquals(7, snapshot.values()[0], 0.0001);
        assertEquals(10, snapshot.values()[3], 0.0001);
        assertEquals(10, series.latest(), 0.0001);
    }

    @Test
    void snapshotComputesMinMaxAndAverage() {
        MetricSeries series = new MetricSeries("frame", "Frame", "ms", 8, 1000);
        series.add(10, 1000);
        series.add(20, 2000);
        series.add(30, 3000);
        MetricSeries.Snapshot snapshot = series.snapshot();
        assertEquals(10, snapshot.min(), 0.0001);
        assertEquals(30, snapshot.max(), 0.0001);
        assertEquals(20, snapshot.average(), 0.0001);
        assertEquals(30, snapshot.latest(), 0.0001);
        assertEquals(2000, snapshot.durationMillis());
    }

    @Test
    void nonFiniteValuesAreIgnored() {
        MetricSeries series = new MetricSeries("fps", "FPS", "", 4, 1000);
        series.add(Double.NaN, 1);
        series.add(Double.POSITIVE_INFINITY, 2);
        assertTrue(series.isEmpty());
        series.add(60, 3);
        assertEquals(1, series.size());
        assertEquals(60, series.latest(), 0.0001);
    }

    @Test
    void clearResetsTheRing() {
        MetricSeries series = new MetricSeries("fps", "FPS", "", 4, 1000);
        series.add(60, 1);
        series.clear();
        assertTrue(series.isEmpty());
        assertTrue(Double.isNaN(series.latest()));
        assertEquals(0, series.snapshot().values().length);
    }
}
