package com.synapmc.mixin;

import com.synapmc.ChatMethods;
import java.util.concurrent.Semaphore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "xyz.wagyourtail.jsmacros.client.api.library.impl.FChat", remap = false)
public abstract class LegacyQueuedChatMixin {
    @Inject(method = "lambda$say$1(Ljava/lang/String;Ljava/util/concurrent/Semaphore;)V",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void synapmc$queuedSay(String message, Semaphore completion, CallbackInfo ci) {
        if (!ChatMethods.canSay(message)) {
            completion.release();
            ci.cancel();
        }
    }
}
