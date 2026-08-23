package com.example.espoints.tile;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Cached world-to-screen points for static tactical-map overlays. Rebuilds only
 * when the caller-supplied frame key changes (typically a quantized viewport
 * plus snapshot revision).
 */
public final class TacticalMapStaticProjection {
    private final Map<String, ScreenPoint> points = new HashMap<>();
    private Object frameKey;
    private long rebuildCount;

    public synchronized void begin(Object key) {
        if (!Objects.equals(frameKey, key)) {
            frameKey = key;
            points.clear();
            rebuildCount++;
        }
    }

    public synchronized ScreenPoint point(String id, double screenX, double screenY) {
        ScreenPoint cached = points.get(id);
        if (cached != null) {
            return cached;
        }
        ScreenPoint created = new ScreenPoint(screenX, screenY);
        points.put(id, created);
        return created;
    }

    public synchronized long rebuildCount() {
        return rebuildCount;
    }

    public synchronized void reset() {
        frameKey = null;
        points.clear();
    }

    public record ScreenPoint(double x, double y) {
    }
}
