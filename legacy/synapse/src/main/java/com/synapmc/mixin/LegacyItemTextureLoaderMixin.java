package com.synapmc.mixin;

import com.synapmc.NativeItemTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.class_1088", remap = false)
public abstract class LegacyItemTextureLoaderMixin {
    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void synapmc$itemModels(CallbackInfo ci) { NativeItemTextures.registerLegacyModels(this); }
}
