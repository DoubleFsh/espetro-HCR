package com.example.espoints.capturepoint;

/**
 * Integrates capture progress from elapsed game ticks.
 *
 * <p>The legacy rule advanced five percentage points every forty ticks. Keeping
 * the value as a double avoids making a shorter configured sampling interval
 * accelerate the game or lose fractional progress.</p>
 */
public final class CaptureProgressIntegrator {
    public static final double LEGACY_PROGRESS_PER_40_TICKS = 5.0D;
    private static final double PROGRESS_PER_TICK =
        LEGACY_PROGRESS_PER_40_TICKS / 40.0D;

    private CaptureProgressIntegrator() {
    }

    public static double advance(double current, int direction, int elapsedTicks) {
        if (!Double.isFinite(current)) {
            throw new IllegalArgumentException("Capture progress must be finite");
        }
        if (direction < -1 || direction > 1 || elapsedTicks < 0) {
            throw new IllegalArgumentException("Invalid capture integration input");
        }
        return clamp(current + direction * elapsedTicks * PROGRESS_PER_TICK);
    }

    public static int display(double preciseProgress) {
        return (int) Math.floor(clamp(preciseProgress) + 1.0E-9D);
    }

    public static double clamp(double progress) {
        return Math.max(0.0D, Math.min(100.0D, progress));
    }
}
