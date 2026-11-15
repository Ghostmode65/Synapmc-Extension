package com.synapmc.mixin;

import com.synapmc.MacroScreenBridge;
import net.minecraft.class_332;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Newer Legacy uses DrawContext instead of MatrixStack for its header fills. */
@Mixin(targets = "xyz.wagyourtail.jsmacros.client.gui.screens.MacroScreen", remap = false)
public abstract class LegacyModernMacroProfileMixin {
    @Redirect(method = "method_25394", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/class_332;method_25294(IIIII)V"), require = 0, remap = false)
    private void synapmc$headerDivider(class_332 context, int x1, int y1, int x2, int y2, int color) {
        MacroScreenBridge.fillHeader(this, context, x1, y1, x2, y2, color);
    }
}
