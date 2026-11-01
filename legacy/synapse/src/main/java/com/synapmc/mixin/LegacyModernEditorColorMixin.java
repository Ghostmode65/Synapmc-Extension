package com.synapmc.mixin;

import com.synapmc.EditorColors;
import net.minecraft.class_332;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "xyz.wagyourtail.jsmacros.client.gui.screens.EditorScreen", remap = false)
public abstract class LegacyModernEditorColorMixin {
    @Unique private final EditorColors synapmc$modernColors = new EditorColors();
    @Inject(method = "method_25394(Lnet/minecraft/class_332;IIF)V", at = @At(value = "INVOKE",
            target = "Lxyz/wagyourtail/jsmacros/client/gui/editor/highlighting/AbstractRenderCodeCompiler;getRenderedText()[Lnet/minecraft/class_2561;"), require = 0, remap = false)
    private void synapmc$highlightColors(class_332 context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        synapmc$modernColors.render(this, context);
    }
}
