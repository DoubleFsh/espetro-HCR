package com.example.espoints.tile;

import com.example.espoints.config.MapImageQuality;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TacticalMapLodPlannerTest {
    private static final long CACHE_BYTES = 64L * 1024L * 1024L;
    private static final TacticalMapPyramidLayout LAYOUT =
        new TacticalMapPyramidLayout(5904, 6720);

    @Test
    void appliesQualityTargetsAfterStableViewAndNeverRefinesPastL0() {
        MutableStates states = new MutableStates();
        TacticalMapLodPlanner planner = new TacticalMapLodPlanner();
        TacticalMapLodPlanner.Viewport view =
            new TacticalMapLodPlanner.Viewport(0.0D, 0.0D, 0.25D, 0.25D, 256, 256);

        TacticalMapLodPlanner.Plan initial = planner.plan(
            LAYOUT, MapImageQuality.PERFORMANCE, view, 1_000L,
            CACHE_BYTES, states);
        assertEquals(2, initial.baseLevel());
        states.ready(initial.layers().get(0).visibleTiles());

        TacticalMapLodPlanner.Plan performance = planner.plan(
            LAYOUT, MapImageQuality.PERFORMANCE, view, 1_250L,
            CACHE_BYTES, states);
        assertEquals(List.of(2), levels(performance));

        TacticalMapLodPlanner.Plan balanced = planner.plan(
            LAYOUT, MapImageQuality.BALANCED, view, 1_250L,
            CACHE_BYTES, states);
        assertEquals(List.of(2, 1), levels(balanced));

        TacticalMapLodPlanner.Plan highFirstStep = planner.plan(
            LAYOUT, MapImageQuality.HIGH, view, 1_250L,
            CACHE_BYTES, states);
        assertEquals(List.of(2, 1), levels(highFirstStep));
        states.ready(highFirstStep.layers().get(1).visibleTiles());
        TacticalMapLodPlanner.Plan highSecondStep = planner.plan(
            LAYOUT, MapImageQuality.HIGH, view, 1_251L,
            CACHE_BYTES, states);
        assertEquals(List.of(2, 1, 0), levels(highSecondStep));

        TacticalMapLodPlanner l0Planner = new TacticalMapLodPlanner();
        TacticalMapLodPlanner.Viewport closeView =
            new TacticalMapLodPlanner.Viewport(0.4D, 0.4D, 0.5D, 0.5D, 1024, 1024);
        TacticalMapLodPlanner.Plan l0 = l0Planner.plan(
            LAYOUT, MapImageQuality.HIGH, closeView, 2_000L,
            CACHE_BYTES, states);
        assertEquals(0, l0.baseLevel());
        assertEquals(List.of(0), levels(l0));
    }

    @Test
    void waits250MillisAndViewMovementRestartsStabilityWindow() {
        MutableStates states = new MutableStates();
        TacticalMapLodPlanner planner = new TacticalMapLodPlanner();
        TacticalMapLodPlanner.Viewport view =
            new TacticalMapLodPlanner.Viewport(0.0D, 0.0D, 0.25D, 0.25D, 256, 256);
        TacticalMapLodPlanner.Plan initial = planner.plan(
            LAYOUT, MapImageQuality.BALANCED, view, 10_000L,
            CACHE_BYTES, states);
        states.ready(initial.layers().get(0).visibleTiles());

        assertEquals(List.of(2), levels(planner.plan(
            LAYOUT, MapImageQuality.BALANCED, view, 10_249L,
            CACHE_BYTES, states)));
        assertEquals(List.of(2, 1), levels(planner.plan(
            LAYOUT, MapImageQuality.BALANCED, view, 10_250L,
            CACHE_BYTES, states)));

        TacticalMapLodPlanner.Viewport dragged =
            new TacticalMapLodPlanner.Viewport(0.01D, 0.0D, 0.26D, 0.25D, 256, 256);
        TacticalMapLodPlanner.Plan reset = planner.plan(
            LAYOUT, MapImageQuality.BALANCED, dragged, 10_300L,
            CACHE_BYTES, states);
        assertFalse(reset.stable());
        assertEquals(1, reset.layers().size());
    }

    @Test
    void textureBudgetCoarsensFull4kViewButAllowsLocalOriginalTiles() {
        MutableStates states = new MutableStates();
        TacticalMapLodPlanner planner = new TacticalMapLodPlanner();
        TacticalMapLodPlanner.Viewport full4k =
            new TacticalMapLodPlanner.Viewport(0.0D, 0.0D, 1.0D, 1.0D, 4096, 4096);
        TacticalMapLodPlanner.Plan initial = planner.plan(
            LAYOUT, MapImageQuality.HIGH, full4k, 1_000L,
            CACHE_BYTES, states);
        assertEquals(1, initial.baseLevel());
        assertTrue(initial.estimatedRgbaBytes() <= CACHE_BYTES * 3L / 4L);
        states.ready(initial.layers().get(0).visibleTiles());
        TacticalMapLodPlanner.Plan stable = planner.plan(
            LAYOUT, MapImageQuality.HIGH, full4k, 1_250L,
            CACHE_BYTES, states);
        assertEquals(List.of(1), levels(stable));

        TacticalMapLodPlanner localPlanner = new TacticalMapLodPlanner();
        TacticalMapLodPlanner.Viewport local =
            new TacticalMapLodPlanner.Viewport(0.45D, 0.45D, 0.55D, 0.55D, 4096, 4096);
        TacticalMapLodPlanner.Plan localPlan = localPlanner.plan(
            LAYOUT, MapImageQuality.HIGH, local, 2_000L,
            CACHE_BYTES, states);
        assertEquals(0, localPlan.baseLevel());
    }

    @Test
    void ordersVisibleRequestsBeforeBasePrefetchAndDoesNotRepeatPendingTiles() {
        MutableStates states = new MutableStates();
        TacticalMapLodPlanner planner = new TacticalMapLodPlanner();
        TacticalMapLodPlanner.Viewport view =
            new TacticalMapLodPlanner.Viewport(0.25D, 0.25D, 0.5D, 0.5D, 256, 256);
        TacticalMapLodPlanner.Plan initial = planner.plan(
            LAYOUT, MapImageQuality.BALANCED, view, 1_000L,
            CACHE_BYTES, states);
        List<TacticalMapPyramidLayout.TileCoordinate> visible =
            initial.layers().get(0).visibleTiles();
        assertEquals(visible, initial.requests().subList(0, visible.size()));
        assertTrue(initial.requests().stream().skip(visible.size())
            .allMatch(tile -> tile.level() == initial.baseLevel()));

        states.requested(initial.requests());
        TacticalMapLodPlanner.Plan duplicateFrame = planner.plan(
            LAYOUT, MapImageQuality.BALANCED, view, 1_001L,
            CACHE_BYTES, states);
        assertTrue(duplicateFrame.requests().isEmpty());

        states.ready(visible);
        TacticalMapLodPlanner.Plan refinement = planner.plan(
            LAYOUT, MapImageQuality.BALANCED, view, 1_250L,
            CACHE_BYTES, states);
        List<TacticalMapPyramidLayout.TileCoordinate> finerVisible =
            LAYOUT.visibleTiles(initial.baseLevel() - 1,
                view.minX(), view.minY(), view.maxX(), view.maxY(), 0);
        assertEquals(finerVisible, refinement.requests());
        assertTrue(refinement.requests().stream()
            .allMatch(tile -> tile.level() == initial.baseLevel() - 1));

        states.requested(refinement.requests());
        assertTrue(planner.plan(
            LAYOUT, MapImageQuality.BALANCED, view, 1_251L,
            CACHE_BYTES, states).requests().isEmpty());
    }

    private static List<Integer> levels(TacticalMapLodPlanner.Plan plan) {
        return plan.layers().stream().map(TacticalMapLodPlanner.Layer::level).toList();
    }

    private static final class MutableStates
            implements TacticalMapLodPlanner.TileStateLookup {
        private final Map<TacticalMapPyramidLayout.TileCoordinate,
            TacticalMapLodPlanner.TileState> values = new HashMap<>();

        @Override
        public TacticalMapLodPlanner.TileState state(
                TacticalMapPyramidLayout.TileCoordinate tile) {
            return values.getOrDefault(tile, TacticalMapLodPlanner.TileState.MISSING);
        }

        private void ready(List<TacticalMapPyramidLayout.TileCoordinate> tiles) {
            tiles.forEach(tile -> values.put(tile, TacticalMapLodPlanner.TileState.READY));
        }

        private void requested(List<TacticalMapPyramidLayout.TileCoordinate> tiles) {
            tiles.forEach(tile -> values.put(tile, TacticalMapLodPlanner.TileState.REQUESTED));
        }
    }
}
