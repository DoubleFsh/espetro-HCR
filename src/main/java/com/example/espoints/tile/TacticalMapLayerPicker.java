package com.example.espoints.tile;

import java.util.List;
import java.util.function.Predicate;

/**
 * Chooses a single complete covering LOD. Stacking coarse-then-fine is what
 * paints the horizontal lake stripes: a missing fine row leaves the arrival
 * layer visible as a band of the wrong resolution.
 */
public final class TacticalMapLayerPicker {
    private TacticalMapLayerPicker() {
    }

    public static TacticalMapLodPlanner.Layer finestCovering(
            TacticalMapPyramidLayout layout,
            List<TacticalMapLodPlanner.Layer> layers,
            Predicate<List<TacticalMapPyramidLayout.TileCoordinate>> ready,
            double minX, double minY, double maxX, double maxY) {
        if (layout == null || layers == null || ready == null) {
            return null;
        }
        TacticalMapLodPlanner.Layer finest = null;
        for (TacticalMapLodPlanner.Layer layer : layers) {
            if (layer == null || layer.visibleTiles() == null || layer.visibleTiles().isEmpty()) {
                continue;
            }
            if (ready.test(layer.visibleTiles())
                && layout.tilesCover(layer.level(), layer.visibleTiles(),
                    minX, minY, maxX, maxY)) {
                finest = layer;
            }
        }
        return finest;
    }
}
