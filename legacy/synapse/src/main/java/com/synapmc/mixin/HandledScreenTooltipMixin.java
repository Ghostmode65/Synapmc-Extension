package com.synapmc.mixin;

import com.synapmc.ItemTooltipContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.class_465", remap = false)
public abstract class HandledScreenTooltipMixin {
    @Inject(method = "method_2380", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/class_332;method_51437(Lnet/minecraft/class_327;Ljava/util/List;Ljava/util/Optional;II)V"), require = 1)
    private void synapmc$beginInventoryTooltip(@Coerce Object context, int mouseX, int mouseY, CallbackInfo ci) {
        ((ItemTooltipContext) context).synapmc$enterItemTooltip();
    }

    @Inject(method = "method_2380", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/class_332;method_51437(Lnet/minecraft/class_327;Ljava/util/List;Ljava/util/Optional;II)V",
        shift = At.Shift.AFTER), require = 1)
    private void synapmc$endInventoryTooltip(@Coerce Object context, int mouseX, int mouseY, CallbackInfo ci) {
        ((ItemTooltipContext) context).synapmc$leaveItemTooltip();
    }
}
