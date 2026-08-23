package com.example.espoints.tile;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TacticalMapTileScreenMathTest {
    private static final TacticalMapPyramidLayout LAYOUT =
        new TacticalMapPyramidLayout(5904, 6720);
    private static final double BOUNDS_MIN_X = -2720.0D;
    private static final double BOUNDS_MIN_Z = -3744.0D;
    private static final double BOUNDS_WIDTH = 5903.0D;
    private static final double BOUNDS_HEIGHT = 6719.0D;

    @Test
    void neighbouringTilesShareOneRoundedEdgeAtEveryReportedZoom() {
        int[] spans = {640, 1127, 1409, 5375};
        for (int span : spans) {
            double spanZ = span;
            double spanX = span * (BOUNDS_WIDTH / BOUNDS_HEIGHT);
            double viewMinX = 0.0D;
            double viewMinZ = 0.0D;
            int screenW = 360;
            int screenH = 410;
            double scaleX = screenW / spanX;
            double scaleZ = screenH / spanZ;
            double minX = (viewMinX - BOUNDS_MIN_X) / BOUNDS_WIDTH;
            double minY = (viewMinZ - BOUNDS_MIN_Z) / BOUNDS_HEIGHT;
            double maxX = minX + spanX / BOUNDS_WIDTH;
            double maxY = minY + spanZ / BOUNDS_HEIGHT;
            for (int level = 0; level <= 2; level++) {
                List<TacticalMapPyramidLayout.TileCoordinate> tiles =
                    LAYOUT.visibleTiles(level, minX, minY, maxX, maxY, 0);
                assertNoDestGaps(level, tiles, viewMinX, viewMinZ, scaleX, scaleZ);
            }
        }
    }

    @Test
    void insetUvNeverReachesTheRepeatBoundary() {
        TacticalMapTileScreenMath.BlitUv uv = TacticalMapTileScreenMath.insetUv(512, 512);
        assertEquals(0.5F, uv.uOffset());
        assertEquals(511, uv.uWidth());
        assertTrue(TacticalMapTileScreenMath.maxU(uv) < 1.0D);
        assertTrue(TacticalMapTileScreenMath.maxV(uv) < 1.0D);
        assertTrue(TacticalMapTileScreenMath.maxU(uv) > 0.99D);
    }

    private static void assertNoDestGaps(
            int level, List<TacticalMapPyramidLayout.TileCoordinate> tiles,
            double viewMinX, double viewMinZ, double scaleX, double scaleZ) {
        for (TacticalMapPyramidLayout.TileCoordinate tile : tiles) {
            TacticalMapTileScreenMath.IntRect dest = project(level, tile.x(), tile.y(),
                viewMinX, viewMinZ, scaleX, scaleZ);
            TacticalMapPyramidLayout.TileCoordinate right =
                new TacticalMapPyramidLayout.TileCoordinate(level, tile.x() + 1, tile.y());
            TacticalMapPyramidLayout.TileCoordinate below =
                new TacticalMapPyramidLayout.TileCoordinate(level, tile.x(), tile.y() + 1);
            if (tiles.contains(right)) {
                TacticalMapTileScreenMath.IntRect next = project(level, right.x(), right.y(),
                    viewMinX, viewMinZ, scaleX, scaleZ);
                assertEquals(dest.right(), next.left(),
                    "horizontal dest gap at L" + level + " " + tile);
            }
            if (tiles.contains(below)) {
                TacticalMapTileScreenMath.IntRect next = project(level, below.x(), below.y(),
                    viewMinX, viewMinZ, scaleX, scaleZ);
                assertEquals(dest.bottom(), next.top(),
                    "vertical dest gap at L" + level + " " + tile);
            }
        }
    }

    private static TacticalMapTileScreenMath.IntRect project(
            int level, int tileX, int tileY,
            double viewMinX, double viewMinZ, double scaleX, double scaleZ) {
        return TacticalMapTileScreenMath.project(
            TacticalMapTileScreenMath.tilePixels(LAYOUT, level, tileX, tileY),
            LAYOUT.levelWidth(level), LAYOUT.levelHeight(level),
            BOUNDS_MIN_X, BOUNDS_MIN_Z, BOUNDS_WIDTH, BOUNDS_HEIGHT,
            viewMinX, viewMinZ, scaleX, scaleZ, 20, 40);
    }
}
