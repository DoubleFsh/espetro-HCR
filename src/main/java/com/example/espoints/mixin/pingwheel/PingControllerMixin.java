package com.example.espoints.mixin.pingwheel;

import com.example.espoints.client.TacticalMarkRadialController;
import nx.pingwheel.common.core.PingController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PingController.class, remap = false)
public abstract class PingControllerMixin {

    @Inject(method = "pollPingAction", at = @At("HEAD"), cancellable = true)
    private static void espoints$suppressDefaultBattlePing(float tickDelta, CallbackInfo ci) {
        if (TacticalMarkRadialController.shouldSuppressDefaultPing()) {
            PingController.revokePingAction();
            ci.cancel();
        }
    }
}
