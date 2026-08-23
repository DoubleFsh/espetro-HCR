package com.example.espoints.capturepoint;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CaptureProgressIntegratorTest {
    @ParameterizedTest
    @ValueSource(ints = {1, 5, 40, 100})
    void configuredIntervalsPreserveLegacyWallClockCaptureSpeed(int interval) {
        double progress = 0.0D;
        int elapsed = 0;
        while (elapsed < 800) {
            int step = Math.min(interval, 800 - elapsed);
            progress = CaptureProgressIntegrator.advance(progress, 1, step);
            elapsed += step;
        }
        assertEquals(100.0D, progress, 1.0E-9D);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5, 40, 100})
    void interruptedAndContestedTimeDoesNotChangeTotalActiveDuration(int interval) {
        double progress = 0.0D;
        progress = integrate(progress, 1, 320, interval);
        progress = integrate(progress, 0, 275, interval);
        progress = integrate(progress, 1, 480, interval);
        assertEquals(100.0D, progress, 1.0E-9D);

        progress = integrate(progress, -1, 800, interval);
        assertEquals(0.0D, progress, 1.0E-9D);
    }

    private static double integrate(double initial, int direction,
                                    int totalTicks, int interval) {
        double progress = initial;
        int elapsed = 0;
        while (elapsed < totalTicks) {
            int step = Math.min(interval, totalTicks - elapsed);
            progress = CaptureProgressIntegrator.advance(progress, direction, step);
            elapsed += step;
        }
        return progress;
    }
}
