package com.example.espoints.mixin.pingwheel;

import com.example.espoints.client.PingWheelMarkerBridge;
import nx.pingwheel.common.core.PingView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PingView.class, remap = false)
public abstract class PingViewMixin {

    @Inject(method = "isExpired", at = @At("HEAD"), cancellable = true)
    private void espoints$serverOwnsLifetime(CallbackInfoReturnable<Boolean> cir) {
        if (PingWheelMarkerBridge.isManaged((PingView) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
