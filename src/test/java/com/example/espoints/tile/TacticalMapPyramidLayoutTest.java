package com.example.espoints.tile;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TacticalMapPyramidLayoutTest {
    @Test
    void coversReferenceMapAtEveryLevelWithoutGaps() {
        TacticalMapPyramidLayout layout =
            new TacticalMapPyramidLayout(5904, 6720);

        assertEquals(4, layout.maxLevel());
        for (int level = 0; level <= layout.maxLevel(); level++) {
            int coveredWidth = 0;
            for (int x = 0; x < layout.columns(level); x++) {
                coveredWidth += layout.tileWidth(level, x);
            }
            int coveredHeight = 0;
            for (int y = 0; y < layout.rows(level); y++) {
                coveredHeight += layout.tileHeight(level, y);
            }
            assertEquals(layout.levelWidth(level), coveredWidth);
            assertEquals(layout.levelHeight(level), coveredHeight);
        }
        assertEquals(168, layout.visibleTiles(0, 0, 0, 1, 1, 1).size());
    }

    @Test
    void visibleWindowIncludesEveryIntersectingRow() {
        TacticalMapPyramidLayout layout = new TacticalMapPyramidLayout(5904, 6720);
        // 1127-world-unit view is ~2.2 L0 tiles tall; both rows plus the
        // overlapping third row must be selected or a coarse stripe appears.
        double minY = 0.40D;
        double maxY = 0.40D + 1127.0D / 6719.0D;
        List<TacticalMapPyramidLayout.TileCoordinate> tiles =
            layout.visibleTiles(0, 0.40D, minY, 0.55D, maxY, 0);
        int minRow = tiles.stream().mapToInt(TacticalMapPyramidLayout.TileCoordinate::y).min().orElse(-1);
        int maxRow = tiles.stream().mapToInt(TacticalMapPyramidLayout.TileCoordinate::y).max().orElse(-1);
        assertTrue(maxRow - minRow >= 2);
        assertTrue(layout.tilesCover(0, tiles, 0.40D, minY, 0.55D, maxY));
    }

    @Test
    void choosesLodAndAddsOnlyOnePrefetchRing() {
        TacticalMapPyramidLayout layout =
            new TacticalMapPyramidLayout(5904, 6720);

        assertEquals(3, layout.chooseLevel(1, 1, 1024, 768));
        assertEquals(0, layout.chooseLevel(0.1, 0.1, 1024, 768));
        List<TacticalMapPyramidLayout.TileCoordinate> tiles =
            layout.visibleTiles(0, 0.25, 0.25, 0.5, 0.5, 1);
        assertFalse(tiles.isEmpty());
        assertTrue(tiles.stream().allMatch(tile ->
            layout.isValid(tile.level(), tile.x(), tile.y())));
        assertEquals(List.of(new TacticalMapPyramidLayout.TileCoordinate(0, 0, 0)),
            new TacticalMapPyramidLayout(512, 512)
                .visibleTiles(0, 0, 0, 0, 0, 0));
    }

    @Test
    void rejectsPixelBombsInvalidLevelsAndForgedRings() {
        assertThrows(IllegalArgumentException.class,
            () -> new TacticalMapPyramidLayout(40_000, 1));
        TacticalMapPyramidLayout layout =
            new TacticalMapPyramidLayout(5904, 6720);
        assertThrows(IllegalArgumentException.class, () -> layout.levelWidth(5));
        assertThrows(IllegalArgumentException.class,
            () -> layout.visibleTiles(0, 0, 0, 1, 1, 3));
    }
}
