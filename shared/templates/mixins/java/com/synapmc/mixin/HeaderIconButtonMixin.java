package com.synapmc.mixin;

import com.synapmc.MacroScreenBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces marked refresh button text, preserving native borders and hover. */
@Mixin(targets = "@GUI_PACKAGE@.elements.Button", remap = false)
public abstract class HeaderIconButtonMixin {
    @Inject(method = "renderMessage(L@EDITOR_CONTEXT_INTERNAL@;)V", at = @At("HEAD"), cancellable = true,
            require = @EDITOR_REQUIRE@, remap = false)
    private void synapmc$headerIcon(@EDITOR_CONTEXT@ context, CallbackInfo ci) {
        if (MacroScreenBridge.renderHeaderIcon(this, context)) ci.cancel();
    }
}
