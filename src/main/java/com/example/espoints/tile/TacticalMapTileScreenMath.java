package com.example.espoints.tile;

/**
 * Integer destination rectangles and wrap-safe blit UVs for pyramid tiles.
 *
 * <p>Neighbouring tiles must share the same rounded edge so a 1 px gap cannot
 * show a coarser layer. Minecraft's default wrap is {@code GL_REPEAT}, so a
 * blit that uses UV 1.0 samples the opposite edge of the same tile and paints
 * a stripe at every zoom.</p>
 */
public final class TacticalMapTileScreenMath {
    private TacticalMapTileScreenMath() {
    }

    public record PixelRect(int left, int top, int right, int bottom) {
        public int width() {
            return Math.max(0, right - left);
        }

        public int height() {
            return Math.max(0, bottom - top);
        }

        public boolean isEmpty() {
            return width() <= 0 || height() <= 0;
        }
    }

    public record IntRect(int left, int top, int right, int bottom) {
        public int width() {
            return Math.max(0, right - left);
        }

        public int height() {
            return Math.max(0, bottom - top);
        }

        public boolean isEmpty() {
            return width() <= 0 || height() <= 0;
        }
    }

    public record BlitUv(float uOffset, float vOffset, int uWidth, int vHeight,
                         int textureWidth, int textureHeight) {
    }

    public static PixelRect tilePixels(TacticalMapPyramidLayout layout,
                                       int level, int tileX, int tileY) {
        int left = tileX * TacticalMapPyramidLayout.TILE_SIZE;
        int top = tileY * TacticalMapPyramidLayout.TILE_SIZE;
        return new PixelRect(left, top,
            left + layout.tileWidth(level, tileX),
            top + layout.tileHeight(level, tileY));
    }

    public static IntRect project(PixelRect pixels, int levelWidth, int levelHeight,
                                  double boundsMinX, double boundsMinZ,
                                  double boundsWidth, double boundsHeight,
                                  double viewMinX, double viewMinZ,
                                  double scaleX, double scaleZ,
                                  int screenLeft, int screenTop) {
        if (pixels == null || pixels.isEmpty() || levelWidth <= 0 || levelHeight <= 0
            || !Double.isFinite(boundsWidth) || !Double.isFinite(boundsHeight)
            || boundsWidth <= 0.0D || boundsHeight <= 0.0D
            || !Double.isFinite(scaleX) || !Double.isFinite(scaleZ)) {
            return new IntRect(0, 0, 0, 0);
        }
        double worldLeft = world(boundsMinX, pixels.left(), levelWidth, boundsWidth);
        double worldRight = world(boundsMinX, pixels.right(), levelWidth, boundsWidth);
        double worldTop = world(boundsMinZ, pixels.top(), levelHeight, boundsHeight);
        double worldBottom = world(boundsMinZ, pixels.bottom(), levelHeight, boundsHeight);
        int destLeft = (int) Math.round(screenLeft + (worldLeft - viewMinX) * scaleX);
        int destRight = (int) Math.round(screenLeft + (worldRight - viewMinX) * scaleX);
        int destTop = (int) Math.round(screenTop + (worldTop - viewMinZ) * scaleZ);
        int destBottom = (int) Math.round(screenTop + (worldBottom - viewMinZ) * scaleZ);
        return new IntRect(destLeft, destTop, destRight, destBottom);
    }

    /**
     * Half-texel inset so {@code GuiGraphics.blit} never emits UV 0 or 1.
     * {@code maxU = (0.5 + (w-1)) / w = (w-0.5)/w}.
     */
    public static BlitUv insetUv(int textureWidth, int textureHeight) {
        int width = Math.max(1, textureWidth);
        int height = Math.max(1, textureHeight);
        if (width <= 1 || height <= 1) {
            return new BlitUv(0.0F, 0.0F, width, height, width, height);
        }
        return new BlitUv(0.5F, 0.5F, width - 1, height - 1, width, height);
    }

    public static double maxU(BlitUv uv) {
        return (uv.uOffset() + uv.uWidth()) / (double) uv.textureWidth();
    }

    public static double maxV(BlitUv uv) {
        return (uv.vOffset() + uv.vHeight()) / (double) uv.textureHeight();
    }

    private static double world(double origin, int pixel, int levelSize, double span) {
        return origin + pixel / (double) levelSize * span;
    }
}
