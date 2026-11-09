package com.logvex.crystal.commandcenter.ui;

/**
 * One named, bounded time series of a single measurement (fps, frame time, memory, ...).
 *
 * <p>It is a fixed-size ring of samples taken at a controlled interval, so the memory cost is
 * constant no matter how long the client runs, and the Command Center can draw a history
 * chart without keeping every frame. Statistics (min, max, average, latest) are computed on
 * demand from the ring, and a caller that wants a chart reads {@link #snapshot()} once per
 * redraw rather than scanning per pixel.
 *
 * <p>Minecraft-independent and allocation-light: appending a sample is one array write, and
 * the ring never grows.
 */
public final class MetricSeries {

    /** A read-only copy of the ring, oldest first, for charting. */
    public record Snapshot(String label, String unit, double[] values, double min, double max,
                           double average, double latest, long firstAtMillis, long lastAtMillis,
                           long intervalMillis) {
        public boolean isEmpty() {
            return values.length == 0;
        }

        /** How long the retained window covers, in milliseconds. */
        public long durationMillis() {
            return lastAtMillis <= firstAtMillis ? 0 : lastAtMillis - firstAtMillis;
        }
    }

    private final String id;
    private final String label;
    private final String unit;
    private final long intervalMillis;
    private final double[] values;
    private final long[] stamps;
    private int next;
    private int filled;

    public MetricSeries(String id, String label, String unit, int capacity, long intervalMillis) {
        this.id = id == null ? "" : id;
        this.label = label == null ? id : label;
        this.unit = unit == null ? "" : unit;
        this.intervalMillis = Math.max(1L, intervalMillis);
        int cap = Math.max(2, capacity);
        this.values = new double[cap];
        this.stamps = new long[cap];
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String unit() {
        return unit;
    }

    public long intervalMillis() {
        return intervalMillis;
    }

    public int capacity() {
        return values.length;
    }

    public int size() {
        return filled;
    }

    public boolean isEmpty() {
        return filled == 0;
    }

    /** Records one sample. Non-finite values are ignored so a bad reading cannot poison the chart. */
    public void add(double value, long atMillis) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return;
        }
        values[next] = value;
        stamps[next] = atMillis;
        next = (next + 1) % values.length;
        if (filled < values.length) {
            filled++;
        }
    }

    public void clear() {
        next = 0;
        filled = 0;
    }

    /** The most recent sample, or {@code Double.NaN} when empty. */
    public double latest() {
        if (filled == 0) {
            return Double.NaN;
        }
        int index = (next - 1 + values.length) % values.length;
        return values[index];
    }

    /**
     * Copies the ring into a chart-ready snapshot, oldest first. Cheap enough to call once per
     * redraw at the chart's refresh interval; callers must not call it per frame.
     */
    public Snapshot snapshot() {
        double[] ordered = new double[filled];
        long[] orderedStamps = new long[filled];
        int start = filled == values.length ? next : 0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        double sum = 0;
        for (int i = 0; i < filled; i++) {
            int index = (start + i) % values.length;
            double v = values[index];
            ordered[i] = v;
            orderedStamps[i] = stamps[index];
            min = Math.min(min, v);
            max = Math.max(max, v);
            sum += v;
        }
        if (filled == 0) {
            min = 0;
            max = 0;
        }
        double avg = filled == 0 ? 0 : sum / filled;
        return new Snapshot(label, unit, ordered, min, max, avg, latest(),
                filled == 0 ? 0 : orderedStamps[0],
                filled == 0 ? 0 : orderedStamps[filled - 1],
                intervalMillis);
    }
}
