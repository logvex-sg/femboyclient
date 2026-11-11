package com.logvex.crystal.diagnostics;

import java.util.Arrays;

/**
 * A bounded window of the world-render pass's own timings and primitive counts.
 *
 * <p>The frame monitor measures how long a whole frame took; this measures the Crystal part
 * of it: how long collecting the ESP primitives and drawing them cost, and how many boxes
 * and labels that was. Without it a slow frame is only a symptom - this is what tells the
 * user whether the ESP pass or something else is responsible.
 *
 * <p>Minecraft-independent and fixed-size: recording is a few array writes, so it is cheap
 * enough to run once per frame from the world-render hook. It only measures; it never
 * changes a setting.
 */
public final class RenderStats {

    /** Frames retained for the averages; about two seconds at 60 fps. */
    public static final int DEFAULT_WINDOW = 120;

    private final double[] collectMillis;
    private final double[] renderMillis;
    private int next;
    private int filled;
    private int lastBoxes;
    private int lastLabels;
    private long lastSampleMillis;

    public RenderStats() {
        this(DEFAULT_WINDOW);
    }

    public RenderStats(int window) {
        int size = Math.max(1, window);
        this.collectMillis = new double[size];
        this.renderMillis = new double[size];
    }

    /**
     * Records one world-render pass.
     *
     * @param collectMillis time spent collecting the ESP primitives, in milliseconds
     * @param renderMillis  time spent drawing them, in milliseconds
     * @param boxes         boxes emitted this pass
     * @param labels        labels emitted this pass
     * @param nowMillis     when the pass finished
     */
    public void record(double collectMillis, double renderMillis, int boxes, int labels, long nowMillis) {
        if (!(collectMillis >= 0) || !(renderMillis >= 0)
                || Double.isNaN(collectMillis) || Double.isNaN(renderMillis)) {
            return;
        }
        this.collectMillis[next] = collectMillis;
        this.renderMillis[next] = renderMillis;
        next = (next + 1) % this.renderMillis.length;
        if (filled < this.renderMillis.length) {
            filled++;
        }
        this.lastBoxes = Math.max(0, boxes);
        this.lastLabels = Math.max(0, labels);
        this.lastSampleMillis = nowMillis;
    }

    /** Clears the window and the last counts. */
    public void reset() {
        Arrays.fill(collectMillis, 0d);
        Arrays.fill(renderMillis, 0d);
        next = 0;
        filled = 0;
        lastBoxes = 0;
        lastLabels = 0;
    }

    /** How many passes are in the window. */
    public int sampleCount() {
        return filled;
    }

    public int lastBoxes() {
        return lastBoxes;
    }

    public int lastLabels() {
        return lastLabels;
    }

    public long lastSampleMillis() {
        return lastSampleMillis;
    }

    /** Mean collection time over the window, or {@code -1} when unmeasured. */
    public double avgCollectMillis() {
        return average(collectMillis);
    }

    /** Mean draw time over the window, or {@code -1} when unmeasured. */
    public double avgRenderMillis() {
        return average(renderMillis);
    }

    private double average(double[] values) {
        if (filled == 0) {
            return -1d;
        }
        double sum = 0;
        for (int i = 0; i < filled; i++) {
            sum += values[i];
        }
        return sum / filled;
    }
}
