package com.example.espoints.tile;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Cached unit-circle vertices with screen-radius-adaptive tessellation. */
public final class TacticalMapCircleMesh {
    private static final Map<Integer, double[]> CACHE = new ConcurrentHashMap<>();

    private TacticalMapCircleMesh() {
    }

    public static int segments(double pixelRadius) {
        if (!Double.isFinite(pixelRadius) || pixelRadius <= 0.0D) {
            return 16;
        }
        // Roughly one segment per six circumference pixels, quantized for reuse.
        int target = (int) Math.ceil(Math.PI * 2.0D * pixelRadius / 6.0D);
        if (target <= 24) return 24;
        if (target <= 36) return 36;
        if (target <= 48) return 48;
        return 72;
    }

    /** Interleaved x/y vertices, including a duplicate closing vertex. */
    public static double[] vertices(int segments) {
        if (segments < 8 || segments > 128) {
            throw new IllegalArgumentException("Invalid circle segment count");
        }
        return CACHE.computeIfAbsent(segments, TacticalMapCircleMesh::create);
    }

    private static double[] create(int segments) {
        double[] values = new double[(segments + 1) * 2];
        for (int index = 0; index <= segments; index++) {
            double angle = Math.PI * 2.0D * index / segments;
            values[index * 2] = Math.cos(angle);
            values[index * 2 + 1] = Math.sin(angle);
        }
        return values;
    }
}
