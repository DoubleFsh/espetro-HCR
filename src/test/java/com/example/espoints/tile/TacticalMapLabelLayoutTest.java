package com.example.espoints.tile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TacticalMapLabelLayoutTest {
    @Test
    void crowdedInputIsDeterministicAndDegradesLowerPriority() {
        TacticalMapLabelLayout first = new TacticalMapLabelLayout();
        TacticalMapLabelLayout second = new TacticalMapLabelLayout();
        TacticalMapLabelLayout.Bounds bounds =
            new TacticalMapLabelLayout.Bounds(0, 0, 80, 30);
        first.begin("frame", bounds);
        second.begin("frame", bounds);

        TacticalMapLabelLayout.Placement highA =
            first.place("capture:a", 2, 30, 10, 4, -4, 42, 18, 9);
        TacticalMapLabelLayout.Placement highB =
            second.place("capture:a", 2, 30, 10, 4, -4, 42, 18, 9);
        assertEquals(highA, highB);
        TacticalMapLabelLayout.Placement lowA =
            first.place("marker:b", 4, 30, 10, 4, -4, 42, 18, 9);
        TacticalMapLabelLayout.Placement lowB =
            second.place("marker:b", 4, 30, 10, 4, -4, 42, 18, 9);
        assertEquals(lowA, lowB);
        assertNotEquals(highA, lowA);
    }

    @Test
    void identicalIdleFrameDoesNotRebuildLayout() {
        TacticalMapLabelLayout layout = new TacticalMapLabelLayout();
        TacticalMapLabelLayout.Bounds bounds =
            new TacticalMapLabelLayout.Bounds(0, 0, 100, 100);
        for (int frame = 0; frame < 1_000; frame++) {
            layout.begin("unchanged", bounds);
            layout.place("base", 2, 50, 50, 4, -4, 30, 12, 9);
        }
        assertEquals(1L, layout.rebuildCount());
    }
}
