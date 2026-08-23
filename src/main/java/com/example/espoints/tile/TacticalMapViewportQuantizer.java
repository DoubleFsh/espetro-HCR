package com.example.espoints.tile;

/**
 * Quantizes a world-space viewport to a small number of screen pixels so
 * interpolated follow-camera motion does not rebuild static label layouts
 * every frame.
 */
public final class TacticalMapViewportQuantizer {
    public static final int LABEL_PIXELS = 4;

    private TacticalMapViewportQuantizer() {
    }

    public static long quantize(double world, double span, int screenPixels, int stepPixels) {
        double worldPerPixel = screenPixels <= 0 ? 1.0D : span / screenPixels;
        double step = Math.max(worldPerPixel * Math.max(1, stepPixels), 1.0e-6D);
        return Math.round(world / step);
    }

    public static LabelKey labelKey(
            long revision, double minX, double minZ, double maxX, double maxZ,
            double spanX, double spanZ, int width, int height,
            boolean compact, boolean showLabels) {
        return new LabelKey(
            revision,
            quantize(minX, spanX, width, LABEL_PIXELS),
            quantize(minZ, spanZ, height, LABEL_PIXELS),
            quantize(maxX, spanX, width, LABEL_PIXELS),
            quantize(maxZ, spanZ, height, LABEL_PIXELS),
            width, height, compact, showLabels);
    }

    public record LabelKey(
        long revision, long minX, long minZ, long maxX, long maxZ,
        int width, int height, boolean compact, boolean showLabels) {
    }
}
