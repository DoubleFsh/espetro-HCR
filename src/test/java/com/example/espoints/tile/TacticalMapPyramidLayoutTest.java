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
