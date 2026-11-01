package com.synapmc.mixin;

import com.synapmc.ChatMethods;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(targets = "@CORE_PACKAGE@.client.api.library.impl.FChat", remap = false)
public abstract class ChatMixin {
    @Inject(method = "say(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void synapmc$canSay(String message, boolean await, CallbackInfo ci) {
        if (!ChatMethods.canSay(message)) ci.cancel();
    }

    @Inject(method = "sayInternal", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void synapmc$queuedSay(String message, CallbackInfo ci) {
        if (!ChatMethods.canSay(message)) ci.cancel();
    }
}
