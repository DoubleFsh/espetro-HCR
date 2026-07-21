package com.example.espoints.client;

import cc.sighs.auratip.api.client.TipClientApi;
import cc.sighs.auratip.api.tip.TipBuilder;
import cc.sighs.auratip.client.render.TipOverlay;
import cc.sighs.auratip.data.TipData;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AuraTip presenter for the standard ESPoints match notification style.
 */
public final class AuraTipMessagePopup {

    private static final AtomicLong NEXT_ID = new AtomicLong();

    private AuraTipMessagePopup() {
    }

    public static boolean show(String message, long durationMillis, int width, int height,
                               int backgroundColor, int borderColor) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || message == null || message.isBlank()
                || TipOverlay.INSTANCE.isActive()) {
            return false;
        }

        int availableWidth = Math.max(140,
            minecraft.getWindow().getGuiScaledWidth() - 24);
        int resolvedWidth = Math.min(availableWidth, Math.max(180,
            Math.min(340, Math.max(width, minecraft.font.width(message) + 28))));
        int resolvedHeight = Math.max(52, height);
        int durationTicks = Math.max(1, (int) Math.ceil(durationMillis / 50.0D));
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
            "espoints", "message_" + NEXT_ID.incrementAndGet());

        TipData tip = new TipBuilder(id)
            .visual(visual -> visual
                .size(resolvedWidth, resolvedHeight)
                .positionPreset("RIGHT_CENTER")
                .animationStyle(ResourceLocation.fromNamespaceAndPath(
                    "auratip", "slide_in_right"))
                .animationSpeed(1.4f)
                .themeColor(argb(borderColor))
                .stripeWidth(3)
                .background(TipData.VisualSettings.BackgroundType.SOLID,
                    List.of(argb(backgroundColor)), 0)
                .backgroundRounded(false))
            .behavior(behavior -> behavior
                .duration(durationTicks)
                .pauseOnHover(true)
                .allowPaging(false))
            .page(0, page -> page.content(Component.literal(message)))
            .build();
        TipClientApi.enqueue(List.of(tip), Map.of());
        return true;
    }

    private static String argb(int color) {
        return String.format("#%08X", color);
    }
}
