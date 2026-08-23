package com.example.espoints.tile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic cached collision layout for static tactical-map labels.
 * Callers submit candidates in priority order (smaller value is more important).
 */
public final class TacticalMapLabelLayout {
    private final Map<String, Placement> placements = new HashMap<>();
    private final List<Occupied> occupied = new ArrayList<>();
    private Object frameKey;
    private Bounds bounds = new Bounds(0, 0, 1, 1);
    private long rebuildCount;

    public synchronized void begin(Object key, Bounds newBounds) {
        Objects.requireNonNull(newBounds, "newBounds");
        if (!Objects.equals(frameKey, key) || !bounds.equals(newBounds)) {
            frameKey = key;
            bounds = newBounds;
            placements.clear();
            occupied.clear();
            rebuildCount++;
        }
    }

    public synchronized Placement place(
            String id, int priority, int anchorX, int anchorY,
            int preferredOffsetX, int preferredOffsetY,
            int fullWidth, int abbreviatedWidth, int height) {
        Placement cached = placements.get(id);
        if (cached != null) {
            return cached;
        }
        int safeHeight = Math.max(1, height);
        int[][] offsets = {
            {preferredOffsetX, preferredOffsetY},
            {preferredOffsetX, preferredOffsetY + safeHeight + 3},
            {-Math.max(1, preferredOffsetX) - fullWidth, preferredOffsetY},
            {-fullWidth / 2, preferredOffsetY - safeHeight - 3},
            {-fullWidth / 2, preferredOffsetY + safeHeight + 3}
        };
        Placement result = tryWidths(
            priority, anchorX, anchorY, offsets, Math.max(1, fullWidth),
            safeHeight, Mode.FULL);
        if (result == null && abbreviatedWidth > 0 && abbreviatedWidth < fullWidth) {
            result = tryWidths(priority, anchorX, anchorY, offsets,
                abbreviatedWidth, safeHeight, Mode.ABBREVIATED);
        }
        if (result == null) {
            result = new Placement(anchorX, anchorY, Mode.ICON_ONLY);
        }
        placements.put(id, result);
        return result;
    }

    public synchronized long rebuildCount() {
        return rebuildCount;
    }

    public synchronized void reset() {
        frameKey = null;
        placements.clear();
        occupied.clear();
    }

    private Placement tryWidths(int priority, int anchorX, int anchorY,
                                int[][] offsets, int width, int height, Mode mode) {
        for (int[] offset : offsets) {
            int x = clamp(anchorX + offset[0], bounds.left(), bounds.right() - width);
            int y = clamp(anchorY + offset[1], bounds.top(), bounds.bottom() - height);
            Occupied candidate = new Occupied(priority, x, y, x + width, y + height);
            if (occupied.stream().noneMatch(candidate::overlaps)) {
                occupied.add(candidate);
                return new Placement(x, y, mode);
            }
        }
        return null;
    }

    private static int clamp(int value, int minimum, int maximum) {
        if (maximum < minimum) return minimum;
        return Math.max(minimum, Math.min(maximum, value));
    }

    public enum Mode {
        FULL,
        ABBREVIATED,
        ICON_ONLY
    }

    public record Placement(int x, int y, Mode mode) {
    }

    public record Bounds(int left, int top, int right, int bottom) {
        public Bounds {
            if (right <= left) {
                right = left + 1;
            }
            if (bottom <= top) {
                bottom = top + 1;
            }
        }
    }

    private record Occupied(int priority, int left, int top, int right, int bottom) {
        private boolean overlaps(Occupied other) {
            return left < other.right && right > other.left
                && top < other.bottom && bottom > other.top;
        }
    }
}
