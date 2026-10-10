package com.synapmc.mixin;

import com.synapmc.NativeItemTextures;
import net.minecraft.class_1799;
import net.minecraft.class_1087;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.class_763", remap = false)
public abstract class LegacyItemTextureModelMixin {
    @Inject(method = "method_3308", at = @At("HEAD"), cancellable = true, remap = false)
    private void synapmc$itemModel(class_1799 stack, CallbackInfoReturnable<class_1087> ci) {
        Object replacement = NativeItemTextures.legacyModel(stack);
        if (replacement != null) ci.setReturnValue((class_1087) replacement);
    }
}
