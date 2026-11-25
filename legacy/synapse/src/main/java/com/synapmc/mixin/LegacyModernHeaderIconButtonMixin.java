package com.synapmc.mixin;

import com.synapmc.MacroScreenBridge;
import net.minecraft.class_332;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Legacy 1.21 uses a GUI context instead of the older matrix stack. */
@Mixin(targets = "xyz.wagyourtail.wagyourgui.elements.Button", remap = false)
public abstract class LegacyModernHeaderIconButtonMixin {
    @Inject(method = "renderMessage(Lnet/minecraft/class_332;)V", at = @At("HEAD"), cancellable = true,
            require = 0, remap = false)
    private void synapmc$headerIcon(class_332 context, CallbackInfo ci) {
        if (MacroScreenBridge.renderHeaderIcon(this, context)) ci.cancel();
    }
}
