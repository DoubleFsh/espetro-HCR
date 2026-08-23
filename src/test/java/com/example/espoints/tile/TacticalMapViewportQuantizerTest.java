package com.example.espoints.tile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TacticalMapViewportQuantizerTest {

    @Test
    void subPixelFollowCameraMotionReusesTheSameLabelKey() {
        TacticalMapViewportQuantizer.LabelKey first = TacticalMapViewportQuantizer.labelKey(
            3L, 0.00, 0.00, 100.0, 100.0, 100.0, 100.0, 200, 200, false, true);
        TacticalMapViewportQuantizer.LabelKey nearby = TacticalMapViewportQuantizer.labelKey(
            3L, 0.80, 0.80, 100.8, 100.8, 100.0, 100.0, 200, 200, false, true);
        TacticalMapViewportQuantizer.LabelKey far = TacticalMapViewportQuantizer.labelKey(
            3L, 8.00, 8.00, 108.0, 108.0, 100.0, 100.0, 200, 200, false, true);
        assertEquals(first, nearby);
        assertNotEquals(first, far);
    }
}
