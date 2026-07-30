package com.example.espoints.mixin.pingwheel;

import com.example.espoints.client.PingWheelMarkerBridge;
import net.minecraft.world.item.ItemStack;
import nx.pingwheel.common.render.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DrawContext.class, remap = false)
public abstract class DrawContextMixin {

    @Inject(method = "renderPing", at = @At("HEAD"), cancellable = true)
    private void espoints$renderTacticalIcon(ItemStack stack, boolean drawItem,
                                             int ignoredColor, CallbackInfo ci) {
        if (PingWheelMarkerBridge.currentType() == null) {
            return;
        }
        ((DrawContext) (Object) this).renderTexture(
            PingWheelMarkerBridge.currentTexture(),
            12,
            PingWheelMarkerBridge.currentTint());
        ci.cancel();
    }
}
