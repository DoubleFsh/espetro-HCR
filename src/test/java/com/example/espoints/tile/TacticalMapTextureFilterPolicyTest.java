package com.example.espoints.tile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TacticalMapTextureFilterPolicyTest {
    @Test
    void smoothsOnlyTheTemporaryCoarsestPreview() {
        assertFalse(TacticalMapTextureFilterPolicy.useLinearFiltering(0, 3));
        assertFalse(TacticalMapTextureFilterPolicy.useLinearFiltering(1, 3));
        assertFalse(TacticalMapTextureFilterPolicy.useLinearFiltering(2, 3));
        assertTrue(TacticalMapTextureFilterPolicy.useLinearFiltering(3, 3));
    }

    @Test
    void keepsASingleOriginalTileSharp() {
        assertFalse(TacticalMapTextureFilterPolicy.useLinearFiltering(0, 0));
    }

    @Test
    void detailedTilesAreSharpOnlyAtExactIntegerPixelRatios() {
        assertFalse(TacticalMapTextureFilterPolicy.useLinearFiltering(
            0, 3, 1.0D, 2.0D));
        assertFalse(TacticalMapTextureFilterPolicy.useLinearFiltering(
            0, 3, 0.5D, 0.25D));
        assertTrue(TacticalMapTextureFilterPolicy.useLinearFiltering(
            0, 3, 1.25D, 2.0D));
        assertTrue(TacticalMapTextureFilterPolicy.useLinearFiltering(
            0, 3, 0.6D, 0.5D));
    }

    @Test
    void rejectsInvalidLevels() {
        assertThrows(IllegalArgumentException.class,
            () -> TacticalMapTextureFilterPolicy.useLinearFiltering(2, 1));
        assertThrows(IllegalArgumentException.class,
            () -> TacticalMapTextureFilterPolicy.useLinearFiltering(
                0, 1, Double.NaN, 1.0D));
    }
}
