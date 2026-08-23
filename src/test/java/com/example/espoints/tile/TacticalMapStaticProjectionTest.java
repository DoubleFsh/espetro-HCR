package com.example.espoints.tile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TacticalMapStaticProjectionTest {
    @Test
    void identicalFrameReusesProjectedPoints() {
        TacticalMapStaticProjection projection = new TacticalMapStaticProjection();
        for (int frame = 0; frame < 1_000; frame++) {
            projection.begin("stable");
            TacticalMapStaticProjection.ScreenPoint point =
                projection.point("base:attack", 10.0 + frame, 20.0 + frame);
            assertEquals(10.0, point.x());
            assertEquals(20.0, point.y());
        }
        assertEquals(1L, projection.rebuildCount());
    }

    @Test
    void keyChangeRebuildsAndDropsStalePoints() {
        TacticalMapStaticProjection projection = new TacticalMapStaticProjection();
        projection.begin("a");
        projection.point("base", 1.0, 2.0);
        projection.begin("b");
        TacticalMapStaticProjection.ScreenPoint moved = projection.point("base", 8.0, 9.0);
        assertEquals(8.0, moved.x());
        assertEquals(2L, projection.rebuildCount());
        assertNotEquals(1.0, moved.x());
    }
}
