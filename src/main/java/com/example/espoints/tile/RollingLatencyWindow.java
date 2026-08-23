package com.example.espoints.tile;

import java.util.Arrays;

/** Small allocation-free-on-record rolling latency sample window. */
public final class RollingLatencyWindow {
    private final long[] samples;
    private int cursor;
    private int count;

    public RollingLatencyWindow(int capacity) {
        if (capacity < 1 || capacity > 65_536) {
            throw new IllegalArgumentException("Invalid latency window capacity");
        }
        samples = new long[capacity];
    }

    public synchronized void record(long nanos) {
        samples[cursor] = Math.max(0L, nanos);
        cursor = (cursor + 1) % samples.length;
        count = Math.min(samples.length, count + 1);
    }

    public synchronized long percentile(double quantile) {
        if (!Double.isFinite(quantile) || quantile < 0.0D || quantile > 1.0D) {
            throw new IllegalArgumentException("Invalid latency quantile");
        }
        if (count == 0) {
            return 0L;
        }
        long[] ordered = Arrays.copyOf(samples, count);
        Arrays.sort(ordered);
        int index = (int) Math.ceil(quantile * ordered.length) - 1;
        return ordered[Math.max(0, Math.min(ordered.length - 1, index))];
    }

    public synchronized int size() {
        return count;
    }
}
