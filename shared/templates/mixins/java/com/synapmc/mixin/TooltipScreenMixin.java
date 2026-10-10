package com.synapmc.mixin;

import com.synapmc.TooltipTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "@SCREEN_OWNER@", remap = false)
public abstract class TooltipScreenMixin {
    @Inject(method = "@SET_SCREEN@", at = @At("HEAD"), require = 1)
    private void synapmc$clearTooltipOnScreenChange(CallbackInfo ci) { TooltipTracker.clear(); }
}
