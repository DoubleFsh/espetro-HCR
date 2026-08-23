package com.example.espoints.client;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * Minecraft {@link DynamicTexture} leaves wrap at the GL default {@code REPEAT}.
 * A tile blit whose UV reaches 1.0 then samples the opposite edge and draws
 * a stripe between neighbours at every zoom.
 */
final class TacticalMapTextureSampling {
    private static final int GL_TEXTURE_2D = 3553;
    private static final int GL_TEXTURE_WRAP_S = 10242;
    private static final int GL_TEXTURE_WRAP_T = 10243;
    private static final int GL_CLAMP_TO_EDGE = 33071;

    private TacticalMapTextureSampling() {
    }

    static void apply(DynamicTexture texture, boolean linear) {
        if (texture == null) {
            return;
        }
        texture.setFilter(linear, false);
        texture.bind();
        GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }
}
