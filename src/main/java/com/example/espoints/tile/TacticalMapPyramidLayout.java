package com.example.espoints.tile;

import java.util.ArrayList;
import java.util.List;

/** Pure pyramid/LOD math shared by server validation, client selection and tests. */
public final class TacticalMapPyramidLayout {
    public static final int TILE_SIZE = 512;
    public static final int MAX_LEVELS = 16;
    public static final int MAX_DIMENSION = 32_768;

    private final int width;
    private final int height;
    private final int maxLevel;

    public TacticalMapPyramidLayout(int width, int height) {
        if (width <= 0 || height <= 0
            || width > MAX_DIMENSION || height > MAX_DIMENSION
            || (long) width * height > 64L * 1024L * 1024L) {
            throw new IllegalArgumentException("Invalid tactical map dimensions");
        }
        this.width = width;
        this.height = height;
        int level = 0;
        while ((ceilDivPow2(width, level) > TILE_SIZE
                || ceilDivPow2(height, level) > TILE_SIZE)
            && level < MAX_LEVELS - 1) {
            level++;
        }
        this.maxLevel = level;
    }

    public int width() { return width; }
    public int height() { return height; }
    public int maxLevel() { return maxLevel; }

    public int levelWidth(int level) {
        checkedLevel(level);
        return ceilDivPow2(width, level);
    }

    public int levelHeight(int level) {
        checkedLevel(level);
        return ceilDivPow2(height, level);
    }

    public int columns(int level) {
        return ceilDiv(levelWidth(level), TILE_SIZE);
    }

    public int rows(int level) {
        return ceilDiv(levelHeight(level), TILE_SIZE);
    }

    public int tileWidth(int level, int tileX) {
        checkedLevel(level);
        if (tileX < 0 || tileX >= columns(level)) {
            throw new IllegalArgumentException("Invalid tile x");
        }
        return Math.min(TILE_SIZE, levelWidth(level) - tileX * TILE_SIZE);
    }

    public int tileHeight(int level, int tileY) {
        checkedLevel(level);
        if (tileY < 0 || tileY >= rows(level)) {
            throw new IllegalArgumentException("Invalid tile y");
        }
        return Math.min(TILE_SIZE, levelHeight(level) - tileY * TILE_SIZE);
    }

    public boolean isValid(int level, int tileX, int tileY) {
        return level >= 0 && level <= maxLevel
            && tileX >= 0 && tileX < columns(level)
            && tileY >= 0 && tileY < rows(level);
    }

    public int chooseLevel(double visibleFractionX, double visibleFractionY,
                           int screenWidth, int screenHeight) {
        if (screenWidth <= 0 || screenHeight <= 0) {
            return maxLevel;
        }
        double sourcePixelsPerScreenPixel = Math.max(
            width * Math.max(0.0D, Math.min(1.0D, visibleFractionX)) / screenWidth,
            height * Math.max(0.0D, Math.min(1.0D, visibleFractionY)) / screenHeight);
        if (sourcePixelsPerScreenPixel <= 1.0D) {
            return 0;
        }
        int level = (int) Math.floor(Math.log(sourcePixelsPerScreenPixel) / Math.log(2.0D));
        return Math.max(0, Math.min(maxLevel, level));
    }

    /** Visible tiles plus a one-tile prefetch ring. Fractions are clamped to [0,1]. */
    public List<TileCoordinate> visibleTiles(
            int level, double minX, double minY, double maxX, double maxY, int ring) {
        checkedLevel(level);
        if (ring < 0 || ring > 2) {
            throw new IllegalArgumentException("Invalid tile prefetch ring");
        }
        int width = levelWidth(level);
        int height = levelHeight(level);
        double epsilon = 1.0D / Math.max(width, height);
        double clampedMinX = clampFraction(Math.min(minX, maxX) - epsilon);
        double clampedMaxX = clampFraction(Math.max(minX, maxX) + epsilon);
        double clampedMinY = clampFraction(Math.min(minY, maxY) - epsilon);
        double clampedMaxY = clampFraction(Math.max(minY, maxY) + epsilon);
        int minTileX = Math.max(0,
            (int) Math.floor(clampedMinX * width / TILE_SIZE) - ring);
        int maxTileX = Math.min(columns(level) - 1,
            (int) Math.ceil(clampedMaxX * width / TILE_SIZE) - 1 + ring);
        int minTileY = Math.max(0,
            (int) Math.floor(clampedMinY * height / TILE_SIZE) - ring);
        int maxTileY = Math.min(rows(level) - 1,
            (int) Math.ceil(clampedMaxY * height / TILE_SIZE) - 1 + ring);
        if (maxTileX < minTileX) {
            maxTileX = minTileX;
        }
        if (maxTileY < minTileY) {
            maxTileY = minTileY;
        }
        List<TileCoordinate> result = new ArrayList<>();
        for (int y = minTileY; y <= maxTileY; y++) {
            for (int x = minTileX; x <= maxTileX; x++) {
                result.add(new TileCoordinate(level, x, y));
            }
        }
        return result;
    }

    /** True when the tile set is a solid rectangle that covers the viewport. */
    public boolean tilesCover(int level, List<TileCoordinate> tiles,
                              double minX, double minY, double maxX, double maxY) {
        if (tiles == null || tiles.isEmpty() || !isValid(level, 0, 0)) {
            return false;
        }
        int minTileX = Integer.MAX_VALUE;
        int maxTileX = Integer.MIN_VALUE;
        int minTileY = Integer.MAX_VALUE;
        int maxTileY = Integer.MIN_VALUE;
        for (TileCoordinate tile : tiles) {
            if (tile == null || tile.level() != level) {
                return false;
            }
            minTileX = Math.min(minTileX, tile.x());
            maxTileX = Math.max(maxTileX, tile.x());
            minTileY = Math.min(minTileY, tile.y());
            maxTileY = Math.max(maxTileY, tile.y());
        }
        int expected = (maxTileX - minTileX + 1) * (maxTileY - minTileY + 1);
        if (tiles.size() != expected) {
            return false;
        }
        double left = minTileX * (double) TILE_SIZE / levelWidth(level);
        double top = minTileY * (double) TILE_SIZE / levelHeight(level);
        double right = (maxTileX * (double) TILE_SIZE
            + tileWidth(level, maxTileX)) / (double) levelWidth(level);
        double bottom = (maxTileY * (double) TILE_SIZE
            + tileHeight(level, maxTileY)) / (double) levelHeight(level);
        double epsilon = 1.0D / Math.max(levelWidth(level), levelHeight(level));
        return left <= minX + epsilon && top <= minY + epsilon
            && right >= maxX - epsilon && bottom >= maxY - epsilon;
    }

    private void checkedLevel(int level) {
        if (level < 0 || level > maxLevel) {
            throw new IllegalArgumentException("Invalid tile level: " + level);
        }
    }

    private static int maximumTile(double fraction, int levelSize) {
        double inclusive = fraction <= 0.0D ? 0.0D : Math.nextDown(fraction);
        return (int) Math.floor(inclusive * levelSize / TILE_SIZE);
    }

    private static int ceilDivPow2(int value, int level) {
        return (int) (((long) value + (1L << level) - 1L) >> level);
    }

    private static int ceilDiv(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }

    private static double clampFraction(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    public record TileCoordinate(int level, int x, int y) {
    }
}
