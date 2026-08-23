package com.example.espoints.tile;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TacticalMapImageScalerTest {
    @Test
    void bicubicPyramidScalingAddsOnlyMildEdgeSharpening() {
        BufferedImage source = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                source.setRGB(x, y, x < 16 ? 0xFF202020 : 0xFFE0E0E0);
            }
        }

        BufferedImage bicubic = TacticalMapImageScaler.resizeBicubic(source, 8, 8);
        BufferedImage sharpened = TacticalMapImageScaler.scale(source, 8, 8);
        assertEquals(8, sharpened.getWidth());
        assertEquals(8, sharpened.getHeight());
        assertEquals(0xFF, sharpened.getRGB(4, 4) >>> 24);
        int bicubicContrast = red(bicubic.getRGB(4, 4)) - red(bicubic.getRGB(3, 4));
        int sharpenedContrast = red(sharpened.getRGB(4, 4)) - red(sharpened.getRGB(3, 4));
        assertTrue(sharpenedContrast >= bicubicContrast);
        assertTrue(sharpenedContrast - bicubicContrast < 64);
    }

    @Test
    void p3CacheKeyCannotReuseLegacyCompleteMarkerDirectory() {
        String hash = "a".repeat(64);
        assertEquals(hash + "-p3", TacticalMapTileService.cacheDirectoryName(hash));
        assertNotEquals(hash, TacticalMapTileService.cacheDirectoryName(hash));
    }

    private static int red(int argb) {
        return argb >>> 16 & 0xFF;
    }
}
