package com.example.espoints.tile;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class TacticalMapLayerPickerTest {
    private static final TacticalMapPyramidLayout LAYOUT =
        new TacticalMapPyramidLayout(5904, 6720);

    @Test
    void prefersTheFinestCompleteCoverAndIgnoresAHoledFineLayer() {
        double minX = 0.40D;
        double minY = 0.40D;
        double maxX = 0.40D + 1409.0D / 6719.0D;
        double maxY = 0.40D + 1409.0D / 6719.0D;
        List<TacticalMapPyramidLayout.TileCoordinate> coarse =
            LAYOUT.visibleTiles(2, minX, minY, maxX, maxY, 0);
        List<TacticalMapPyramidLayout.TileCoordinate> fine =
            LAYOUT.visibleTiles(0, minX, minY, maxX, maxY, 0);
        TacticalMapLodPlanner.Layer coarseLayer = new TacticalMapLodPlanner.Layer(2, coarse);
        TacticalMapLodPlanner.Layer fineLayer = new TacticalMapLodPlanner.Layer(0, fine);

        Set<TacticalMapPyramidLayout.TileCoordinate> ready = new HashSet<>(coarse);
        assertSame(coarseLayer, TacticalMapLayerPicker.finestCovering(
            LAYOUT, List.of(coarseLayer, fineLayer), ready::containsAll,
            minX, minY, maxX, maxY));

        ready.addAll(fine.subList(0, Math.max(1, fine.size() - 1)));
        assertSame(coarseLayer, TacticalMapLayerPicker.finestCovering(
            LAYOUT, List.of(coarseLayer, fineLayer), ready::containsAll,
            minX, minY, maxX, maxY));

        ready.addAll(fine);
        assertSame(fineLayer, TacticalMapLayerPicker.finestCovering(
            LAYOUT, List.of(coarseLayer, fineLayer), ready::containsAll,
            minX, minY, maxX, maxY));
    }

    @Test
    void returnsNullWhenNothingCoversTheView() {
        assertNull(TacticalMapLayerPicker.finestCovering(
            LAYOUT, List.of(), tiles -> true, 0, 0, 1, 1));
        List<TacticalMapPyramidLayout.TileCoordinate> one =
            List.of(new TacticalMapPyramidLayout.TileCoordinate(0, 3, 4));
        assertNull(TacticalMapLayerPicker.finestCovering(
            LAYOUT, List.of(new TacticalMapLodPlanner.Layer(0, one)),
            tiles -> true, 0.1D, 0.1D, 0.4D, 0.4D));
        assertNotNull(LAYOUT);
        assertEquals(4, LAYOUT.maxLevel());
    }
}
