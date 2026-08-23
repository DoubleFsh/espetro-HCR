package com.example.espoints.tile;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TacticalMapTileAtlasLayoutTest {
    private static final TacticalMapPyramidLayout LAYOUT =
        new TacticalMapPyramidLayout(5904, 6720);

    @Test
    void stampsNeighbourTilesWithoutAPixelGap() {
        TacticalMapTileAtlasLayout.Spec spec = TacticalMapTileAtlasLayout.spec(LAYOUT, 0, List.of(
            new TacticalMapPyramidLayout.TileCoordinate(0, 1, 2),
            new TacticalMapPyramidLayout.TileCoordinate(0, 2, 2)));
        assertNotNull(spec);
        assertEquals(1024, spec.width());
        assertEquals(512, spec.height());
        assertEquals(0, TacticalMapTileAtlasLayout.atlasX(spec, 1));
        assertEquals(512, TacticalMapTileAtlasLayout.atlasX(spec, 2));

        int[] atlas = new int[spec.width() * spec.height()];
        int[] left = solid(512, 512, 0xFF112233);
        int[] right = solid(512, 512, 0xFF445566);
        TacticalMapTileAtlasLayout.stampRgba(
            atlas, spec.width(), spec.height(), left, 512, 512, 0, 0);
        TacticalMapTileAtlasLayout.stampRgba(
            atlas, spec.width(), spec.height(), right, 512, 512, 512, 0);
        assertEquals(0xFF112233, atlas[511]);
        assertEquals(0xFF445566, atlas[512]);
        assertEquals(0xFF112233, atlas[511 + 511 * spec.width()]);
        assertEquals(0xFF445566, atlas[512 + 511 * spec.width()]);
    }

    @Test
    void rejectsANonRectangularOrHugeSet() {
        assertNull(TacticalMapTileAtlasLayout.spec(LAYOUT, 0, List.of(
            new TacticalMapPyramidLayout.TileCoordinate(0, 0, 0),
            new TacticalMapPyramidLayout.TileCoordinate(0, 2, 0))));
    }

    private static int[] solid(int width, int height, int color) {
        int[] pixels = new int[width * height];
        java.util.Arrays.fill(pixels, color);
        return pixels;
    }
}
