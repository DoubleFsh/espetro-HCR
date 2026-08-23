package com.example.espoints.tile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TacticalMapCircleMeshTest {
    @Test
    void unitVerticesAreCachedAndAdaptToPixelRadius() {
        assertTrue(TacticalMapCircleMesh.segments(10) < TacticalMapCircleMesh.segments(100));
        double[] first = TacticalMapCircleMesh.vertices(36);
        assertSame(first, TacticalMapCircleMesh.vertices(36));
        assertEquals(1.0D, first[0], 1.0E-12D);
        assertEquals(0.0D, first[1], 1.0E-12D);
        assertEquals(first[0], first[first.length - 2], 1.0E-12D);
        assertEquals(first[1], first[first.length - 1], 1.0E-12D);
    }
}
