package com.example.espoints.tile;

/**
 * Chooses texture sampling from LOD role and actual screen/source scale.
 *
 * <p>The single coarsest tile is only a temporary whole-map preview, so linear
 * filtering hides its large pixels. Detailed tiles use nearest only at exact
 * integer pixel ratios; non-integer scaling uses linear sampling to avoid
 * shimmer and discontinuous edge coverage.</p>
 */
public final class TacticalMapTextureFilterPolicy {
    private TacticalMapTextureFilterPolicy() {
    }

    public static boolean useLinearFiltering(int level, int maximumLevel) {
        if (level < 0 || maximumLevel < 0 || level > maximumLevel) {
            throw new IllegalArgumentException("Invalid tactical map LOD");
        }
        return maximumLevel > 0 && level == maximumLevel;
    }

    public static boolean useLinearFiltering(
            int level, int maximumLevel, double scaleX, double scaleY) {
        if (useLinearFiltering(level, maximumLevel)) {
            return true;
        }
        if (!Double.isFinite(scaleX) || !Double.isFinite(scaleY)
            || scaleX <= 0.0D || scaleY <= 0.0D) {
            throw new IllegalArgumentException("Invalid tactical map texture scale");
        }
        return !isExactIntegerRatio(scaleX) || !isExactIntegerRatio(scaleY);
    }

    private static boolean isExactIntegerRatio(double scale) {
        double ratio = scale >= 1.0D ? scale : 1.0D / scale;
        return Math.abs(ratio - Math.rint(ratio)) <= 1.0E-6D;
    }
}
