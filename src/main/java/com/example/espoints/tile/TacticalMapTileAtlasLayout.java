package com.example.espoints.tile;

import java.util.List;

/**
 * Pixel placement for stitching one complete LOD rectangle into a single atlas.
 * The atlas is the only GPU primitive the HUD blits, so tile edges cannot seam.
 */
public final class TacticalMapTileAtlasLayout {
    static final int MAX_ATLAS_EDGE = 4_096;
    static final long MAX_ATLAS_PIXELS = 8_000_000L;

    private TacticalMapTileAtlasLayout() {
    }

    public record Spec(int level, int minTileX, int minTileY, int maxTileX, int maxTileY,
                       int width, int height) {
        public TacticalMapTileScreenMath.PixelRect pixels() {
            return new TacticalMapTileScreenMath.PixelRect(
                minTileX * TacticalMapPyramidLayout.TILE_SIZE,
                minTileY * TacticalMapPyramidLayout.TILE_SIZE,
                minTileX * TacticalMapPyramidLayout.TILE_SIZE + width,
                minTileY * TacticalMapPyramidLayout.TILE_SIZE + height);
        }
    }

    public static Spec spec(TacticalMapPyramidLayout layout, int level,
                            List<TacticalMapPyramidLayout.TileCoordinate> tiles) {
        if (layout == null || tiles == null || tiles.isEmpty() || !layout.isValid(level, 0, 0)) {
            return null;
        }
        int minTileX = Integer.MAX_VALUE;
        int maxTileX = Integer.MIN_VALUE;
        int minTileY = Integer.MAX_VALUE;
        int maxTileY = Integer.MIN_VALUE;
        for (TacticalMapPyramidLayout.TileCoordinate tile : tiles) {
            if (tile == null || tile.level() != level || !layout.isValid(level, tile.x(), tile.y())) {
                return null;
            }
            minTileX = Math.min(minTileX, tile.x());
            maxTileX = Math.max(maxTileX, tile.x());
            minTileY = Math.min(minTileY, tile.y());
            maxTileY = Math.max(maxTileY, tile.y());
        }
        int expected = (maxTileX - minTileX + 1) * (maxTileY - minTileY + 1);
        if (tiles.size() != expected) {
            return null;
        }
        int width = (maxTileX - minTileX) * TacticalMapPyramidLayout.TILE_SIZE
            + layout.tileWidth(level, maxTileX);
        int height = (maxTileY - minTileY) * TacticalMapPyramidLayout.TILE_SIZE
            + layout.tileHeight(level, maxTileY);
        if (width <= 0 || height <= 0
            || width > MAX_ATLAS_EDGE || height > MAX_ATLAS_EDGE
            || (long) width * height > MAX_ATLAS_PIXELS) {
            return null;
        }
        return new Spec(level, minTileX, minTileY, maxTileX, maxTileY, width, height);
    }

    public static int atlasX(Spec spec, int tileX) {
        return (tileX - spec.minTileX()) * TacticalMapPyramidLayout.TILE_SIZE;
    }

    public static int atlasY(Spec spec, int tileY) {
        return (tileY - spec.minTileY()) * TacticalMapPyramidLayout.TILE_SIZE;
    }

    public static void stampRgba(int[] dest, int destWidth, int destHeight,
                                 int[] source, int sourceWidth, int sourceHeight,
                                 int destX, int destY) {
        if (dest == null || source == null || destWidth <= 0 || destHeight <= 0
            || sourceWidth <= 0 || sourceHeight <= 0
            || dest.length < destWidth * destHeight
            || source.length < sourceWidth * sourceHeight) {
            throw new IllegalArgumentException("Invalid atlas stamp buffers");
        }
        for (int row = 0; row < sourceHeight; row++) {
            int targetY = destY + row;
            if (targetY < 0 || targetY >= destHeight) {
                continue;
            }
            int sourceOffset = row * sourceWidth;
            int copyLeft = Math.max(0, -destX);
            int copyRight = Math.min(sourceWidth, destWidth - destX);
            if (copyLeft >= copyRight) {
                continue;
            }
            System.arraycopy(source, sourceOffset + copyLeft,
                dest, targetY * destWidth + destX + copyLeft,
                copyRight - copyLeft);
        }
    }
}
