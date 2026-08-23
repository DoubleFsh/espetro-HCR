package com.example.espoints.tile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RollingLatencyWindowTest {
    @Test
    void reportsUploadP95AndP99FromBoundedRecentSamples() {
        RollingLatencyWindow window = new RollingLatencyWindow(100);
        for (int value = 1; value <= 100; value++) {
            window.record(value);
        }
        assertEquals(95L, window.percentile(0.95D));
        assertEquals(99L, window.percentile(0.99D));
        for (int value = 101; value <= 200; value++) {
            window.record(value);
        }
        assertEquals(195L, window.percentile(0.95D));
        assertEquals(100, window.size());
    }
}
