package com.synapmc.mixin;

import com.synapmc.ItemTextureOverrides;
import com.synapmc.NativeTextureAccess;
import @DRAW_CONTEXT@;
import @STACK_TYPE@;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Replaces one JsMacros GUI item image while preserving native count/durability overlays. */
@Mixin(targets = "@CORE_PACKAGE@.client.api.classes.render.components.Item", remap = false)
public abstract class ItemTextureRenderMixin {
    @Redirect(method = "render(L@DRAW_CONTEXT_INTERNAL@;IIFZ)V", at = @At(value = "INVOKE",
        target = "L@DRAW_CONTEXT_INTERNAL@;@DRAW_ITEM@(L@STACK_INTERNAL@;II)V", remap = false), remap = false)
    private void synapmc$texture(@DRAW_CONTEXT_SIMPLE@ context, @STACK_SIMPLE@ stack, int x, int y,
            @DRAW_CONTEXT_SIMPLE@ originalContext, int mouseX, int mouseY, float delta, boolean is3dRender) {
        if (is3dRender || !ItemTextureOverrides.draw(context, stack, x, y)) NativeTextureAccess.drawVanilla(context, stack, x, y);
    }
}
