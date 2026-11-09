package com.logvex.crystal.commandcenter.ui;

import com.logvex.crystal.commandcenter.PerformanceSample;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The bounded history behind the Performance workspace charts.
 *
 * <p>It owns one {@link MetricSeries} per tracked measurement (fps, frame time, memory, tick
 * time, AI queue depth, background actions) and appends a sample at a controlled interval.
 * The series are fixed-size rings, so a session that runs for hours uses exactly as much
 * memory as one that runs for a minute. Sampling is throttled by the caller
 * ({@link #sampleIfDue}) so the game thread pays at most one cheap append per interval.
 *
 * <p>Minecraft-independent: the caller passes the already-measured numbers, so the whole
 * history is unit testable and nothing here reaches into the live client.
 */
public final class PerformanceHistory {

    /** The ids of the standard series, so the UI can ask for one by name. */
    public static final String FPS = "fps";
    public static final String FRAME_MS = "frame-ms";
    public static final String MEMORY_MB = "memory-mb";
    public static final String TICK_MS = "tick-ms";
    public static final String AI_QUEUE = "ai-queue";
    public static final String ACTIONS = "actions";

    private final Map<String, MetricSeries> series = new LinkedHashMap<>();
    private final long intervalMillis;
    private long lastSampleMillis = Long.MIN_VALUE;

    public PerformanceHistory() {
        this(600, 1000L);
    }

    public PerformanceHistory(int capacity, long intervalMillis) {
        this.intervalMillis = Math.max(1L, intervalMillis);
        series.put(FPS, new MetricSeries(FPS, "FPS", "fps", capacity, intervalMillis));
        series.put(FRAME_MS, new MetricSeries(FRAME_MS, "Frame time", "ms", capacity, intervalMillis));
        series.put(MEMORY_MB, new MetricSeries(MEMORY_MB, "Memory", "MB", capacity, intervalMillis));
        series.put(TICK_MS, new MetricSeries(TICK_MS, "Module tick", "ms", capacity, intervalMillis));
        series.put(AI_QUEUE, new MetricSeries(AI_QUEUE, "AI queue", "req", capacity, intervalMillis));
        series.put(ACTIONS, new MetricSeries(ACTIONS, "Actions", "in flight", capacity, intervalMillis));
    }

    public long intervalMillis() {
        return intervalMillis;
    }

    public MetricSeries series(String id) {
        return series.get(id);
    }

    public List<MetricSeries> all() {
        return List.copyOf(series.values());
    }

    /**
     * Appends a sample when at least one interval has elapsed since the last one.
     *
     * @return true when a sample was recorded
     */
    public boolean sampleIfDue(long nowMillis, PerformanceSample performance, long usedMemoryBytes) {
        if (performance == null) {
            return false;
        }
        if (lastSampleMillis != Long.MIN_VALUE && nowMillis - lastSampleMillis < intervalMillis) {
            return false;
        }
        lastSampleMillis = nowMillis;
        record(performance, usedMemoryBytes, nowMillis);
        return true;
    }

    /** Records a sample unconditionally (used by tests and by an explicit refresh). */
    public void record(PerformanceSample performance, long usedMemoryBytes, long nowMillis) {
        if (performance == null) {
            return;
        }
        if (performance.hasFrameData()) {
            series.get(FPS).add(performance.fps(), nowMillis);
            series.get(FRAME_MS).add(performance.avgFrameMillis(), nowMillis);
        }
        if (usedMemoryBytes > 0) {
            series.get(MEMORY_MB).add(usedMemoryBytes / (1024.0 * 1024.0), nowMillis);
        }
        if (performance.totalModuleTickMillis() >= 0) {
            series.get(TICK_MS).add(performance.totalModuleTickMillis(), nowMillis);
        }
        series.get(AI_QUEUE).add(performance.aiQueueDepth(), nowMillis);
        series.get(ACTIONS).add(performance.activeActions(), nowMillis);
    }

    /** Clears every series (the Performance workspace's clear-history action). */
    public void clear() {
        for (MetricSeries s : series.values()) {
            s.clear();
        }
        lastSampleMillis = Long.MIN_VALUE;
    }

    /** How many samples the fullest series holds, for a "history: N samples" readout. */
    public int sampleCount() {
        int max = 0;
        for (MetricSeries s : series.values()) {
            max = Math.max(max, s.size());
        }
        return max;
    }

    /** The series that carry data, in declaration order. */
    public List<MetricSeries> populated() {
        List<MetricSeries> result = new ArrayList<>();
        for (MetricSeries s : series.values()) {
            if (!s.isEmpty()) {
                result.add(s);
            }
        }
        return result;
    }
}
