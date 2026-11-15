package com.synapmc.mixin;

import com.synapmc.SharedProfiles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "@CORE_PACKAGE@.core.config.ConfigManager", remap = false)
public abstract class ProfileStorageMixin {
    @Inject(method = "saveConfig", at = @At("RETURN"), remap = false)
    private void synapmc$saveSharedProfiles(CallbackInfo ci) { SharedProfiles.nativeSaved(this); }
}
