package com.example.espoints.tile;

import com.example.espoints.config.MapImageQuality;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Stateful, render-API-independent progressive LOD policy.
 *
 * <p>The caller supplies tile state, executes the ordered requests, then renders
 * {@link Plan#layers()} from coarser to finer. A view change immediately starts a
 * new stability window.</p>
 */
public final class TacticalMapLodPlanner {
    public static final long STABLE_VIEW_MILLIS = 250L;
    static final long INACTIVE_VIEW_MILLIS = 1_000L;
    private static final int BUDGET_DENOMINATOR = 4;

    private Viewport previousViewport;
    private long stableSince;
    private long lastPlanAt = Long.MIN_VALUE;

    public Plan plan(TacticalMapPyramidLayout layout,
                     MapImageQuality quality,
                     Viewport viewport,
                     long nowMillis,
                     long textureBudgetBytes,
                     TileStateLookup states) {
        if (layout == null || quality == null || viewport == null || states == null) {
            throw new IllegalArgumentException("LOD plan arguments must be present");
        }
        updateStability(viewport, nowMillis);

        int baseLevel = layout.chooseLevel(
            viewport.visibleFractionX(), viewport.visibleFractionY(),
            viewport.screenWidth(), viewport.screenHeight());
        long refinementBudget = safeFraction(textureBudgetBytes);
        while (baseLevel < layout.maxLevel()
            && visibleLayerBytes(layout, viewport, baseLevel, true) > refinementBudget) {
            baseLevel++;
        }

        List<TacticalMapPyramidLayout.TileCoordinate> baseVisible =
            visibleTiles(layout, viewport, baseLevel);
        List<TacticalMapPyramidLayout.TileCoordinate> basePrefetch =
            prefetchOnly(layout, viewport, baseLevel, baseVisible);
        List<Layer> layers = new ArrayList<>();
        layers.add(new Layer(baseLevel, baseVisible));

        List<TacticalMapPyramidLayout.TileCoordinate> visibleRequests =
            missingTiles(baseVisible, states);
        boolean baseReady = allReady(baseVisible, states);
        boolean stable = nowMillis >= stableSince
            && nowMillis - stableSince >= STABLE_VIEW_MILLIS;

        Set<TacticalMapPyramidLayout.TileCoordinate> budgetedTiles =
            new HashSet<>(baseVisible);
        budgetedTiles.add(new TacticalMapPyramidLayout.TileCoordinate(
            layout.maxLevel(), 0, 0));
        long estimatedBytes = rgbaBytes(layout, budgetedTiles);

        boolean previousLayerReady = baseReady;
        if (stable && baseReady) {
            int refinements = Math.min(quality.refinementLevels(), baseLevel);
            for (int offset = 1; offset <= refinements; offset++) {
                if (!previousLayerReady) {
                    break;
                }
                int level = baseLevel - offset;
                List<TacticalMapPyramidLayout.TileCoordinate> visible =
                    visibleTiles(layout, viewport, level);
                Set<TacticalMapPyramidLayout.TileCoordinate> prospective =
                    new HashSet<>(budgetedTiles);
                prospective.addAll(visible);
                long prospectiveBytes = rgbaBytes(layout, prospective);
                if (prospectiveBytes > refinementBudget) {
                    break;
                }
                budgetedTiles = prospective;
                estimatedBytes = prospectiveBytes;
                layers.add(new Layer(level, visible));
                visibleRequests.addAll(missingTiles(visible, states));
                previousLayerReady = allReady(visible, states);
            }
        }

        // Off-screen prefetch is always base LOD and remains lower priority than
        // every tile that is currently visible.
        List<TacticalMapPyramidLayout.TileCoordinate> requests =
            new ArrayList<>(visibleRequests);
        requests.addAll(missingTiles(basePrefetch, states));
        return new Plan(baseLevel, List.copyOf(layers), List.copyOf(requests),
            stable, estimatedBytes);
    }

    public void reset() {
        previousViewport = null;
        stableSince = 0L;
        lastPlanAt = Long.MIN_VALUE;
    }

    private void updateStability(Viewport viewport, long nowMillis) {
        boolean inactive = lastPlanAt != Long.MIN_VALUE
            && (nowMillis < lastPlanAt || nowMillis - lastPlanAt > INACTIVE_VIEW_MILLIS);
        if (previousViewport == null || !previousViewport.equals(viewport) || inactive) {
            previousViewport = viewport;
            stableSince = nowMillis;
        }
        lastPlanAt = nowMillis;
    }

    private static List<TacticalMapPyramidLayout.TileCoordinate> visibleTiles(
            TacticalMapPyramidLayout layout, Viewport viewport, int level) {
        return layout.visibleTiles(level,
            viewport.minX(), viewport.minY(), viewport.maxX(), viewport.maxY(), 0);
    }

    private static List<TacticalMapPyramidLayout.TileCoordinate> prefetchOnly(
            TacticalMapPyramidLayout layout, Viewport viewport, int level,
            List<TacticalMapPyramidLayout.TileCoordinate> visible) {
        Set<TacticalMapPyramidLayout.TileCoordinate> visibleSet = new HashSet<>(visible);
        List<TacticalMapPyramidLayout.TileCoordinate> result = new ArrayList<>();
        for (TacticalMapPyramidLayout.TileCoordinate tile : layout.visibleTiles(level,
                viewport.minX(), viewport.minY(), viewport.maxX(), viewport.maxY(), 1)) {
            if (!visibleSet.contains(tile)) {
                result.add(tile);
            }
        }
        return result;
    }

    private static List<TacticalMapPyramidLayout.TileCoordinate> missingTiles(
            List<TacticalMapPyramidLayout.TileCoordinate> tiles,
            TileStateLookup states) {
        List<TacticalMapPyramidLayout.TileCoordinate> result = new ArrayList<>();
        for (TacticalMapPyramidLayout.TileCoordinate tile : tiles) {
            if (states.state(tile) == TileState.MISSING) {
                result.add(tile);
            }
        }
        return result;
    }

    private static boolean allReady(
            List<TacticalMapPyramidLayout.TileCoordinate> tiles,
            TileStateLookup states) {
        for (TacticalMapPyramidLayout.TileCoordinate tile : tiles) {
            if (states.state(tile) != TileState.READY) {
                return false;
            }
        }
        return true;
    }

    private static long visibleLayerBytes(TacticalMapPyramidLayout layout,
                                          Viewport viewport, int level,
                                          boolean includePreview) {
        Set<TacticalMapPyramidLayout.TileCoordinate> tiles =
            new LinkedHashSet<>(visibleTiles(layout, viewport, level));
        if (includePreview) {
            tiles.add(new TacticalMapPyramidLayout.TileCoordinate(
                layout.maxLevel(), 0, 0));
        }
        return rgbaBytes(layout, tiles);
    }

    private static long rgbaBytes(TacticalMapPyramidLayout layout,
                                  Set<TacticalMapPyramidLayout.TileCoordinate> tiles) {
        long bytes = 0L;
        for (TacticalMapPyramidLayout.TileCoordinate tile : tiles) {
            bytes += (long) layout.tileWidth(tile.level(), tile.x())
                * layout.tileHeight(tile.level(), tile.y()) * 4L;
        }
        return bytes;
    }

    private static long safeFraction(long bytes) {
        if (bytes <= 0L) {
            return 0L;
        }
        return bytes - bytes / BUDGET_DENOMINATOR;
    }

    public enum TileState {
        MISSING,
        REQUESTED,
        READY
    }

    @FunctionalInterface
    public interface TileStateLookup {
        TileState state(TacticalMapPyramidLayout.TileCoordinate tile);
    }

    public record Viewport(double minX, double minY, double maxX, double maxY,
                           int screenWidth, int screenHeight) {
        public double visibleFractionX() {
            return Math.max(0.0D, Math.min(1.0D, maxX)
                - Math.max(0.0D, Math.min(1.0D, minX)));
        }

        public double visibleFractionY() {
            return Math.max(0.0D, Math.min(1.0D, maxY)
                - Math.max(0.0D, Math.min(1.0D, minY)));
        }
    }

    public record Layer(int level,
                        List<TacticalMapPyramidLayout.TileCoordinate> visibleTiles) {
    }

    public record Plan(int baseLevel, List<Layer> layers,
                       List<TacticalMapPyramidLayout.TileCoordinate> requests,
                       boolean stable, long estimatedRgbaBytes) {
    }
}
