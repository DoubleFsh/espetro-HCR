package com.example.hcrpoints.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;

/**
 * GUI 从下往上的渐入动画辅助类。
 */
public final class ScreenFadeIn {

    private static final int DURATION_MS = 300;
    private static final int START_OFFSET_Y = 24;

    private final long startTime;
    private float progress = 0f;

    public ScreenFadeIn() {
        this.startTime = System.currentTimeMillis();
    }

    public float preRender(GuiGraphics graphics) {
        long elapsed = System.currentTimeMillis() - startTime;
        progress = Math.min(1f, elapsed / (float) DURATION_MS);

        float eased = 1f - (1f - progress) * (1f - progress) * (1f - progress);
        float alpha = eased;
        float offsetY = START_OFFSET_Y * (1f - eased);

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);

        return offsetY;
    }

    public void postRender() {
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    public static void translateY(GuiGraphics graphics, float y) {
        graphics.pose().translate(0, y, 0);
    }
}
