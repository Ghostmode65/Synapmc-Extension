package com.synapmc.mixin;

import com.synapmc.TooltipTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "@GAME_RENDERER@", remap = false)
public abstract class TooltipFrameMixin {
    @Inject(method = "@FRAME_METHOD@", at = @At("HEAD"), require = 1)
    private void synapmc$beginTooltipFrame(CallbackInfo ci) { TooltipTracker.beginFrame(); }
    @Inject(method = "@FRAME_METHOD@", at = @At("RETURN"), require = 1)
    private void synapmc$endTooltipFrame(CallbackInfo ci) { TooltipTracker.endFrame(); }
}
