package com.synapmc.mixin;

import com.synapmc.MiniLogs;
import @CORE_PACKAGE@.core.language.BaseWrappedException;
import @TEXT_TYPE@;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "@CORE_PACKAGE@.client.config.@PROFILE_SIMPLE@", remap = false)
public abstract class ErrorLogMixin {
    @Inject(method = "compileError", at = @At("RETURN"), cancellable = true, remap = false)
    private void synapmc$miniLog(BaseWrappedException<?> error, CallbackInfoReturnable<@TEXT_SIMPLE@> cir) {
        cir.setReturnValue((@TEXT_SIMPLE@) MiniLogs.compact(error, cir.getReturnValue()));
    }
}
