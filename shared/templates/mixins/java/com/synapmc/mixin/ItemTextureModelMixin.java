package com.synapmc.mixin;

import com.synapmc.NativeItemTextures;
import @STACK_TYPE@;
import @COMPONENT_TYPE@;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "@ITEM_RESOLVER@", remap = false)
public abstract class ItemTextureModelMixin {
    @Redirect(method = "@APPEND_ITEM_LAYERS@", at = @At(value = "INVOKE",
        target = "L@STACK_INTERNAL@;@STACK_COMPONENT@(L@COMPONENT_INTERNAL@;)Ljava/lang/Object;", remap = false), remap = false)
    private Object synapmc$itemModel(@STACK_SIMPLE@ stack, @COMPONENT_SIMPLE@<?> component) {
        return NativeItemTextures.modelComponent(stack, stack.@STACK_COMPONENT@(component));
    }
}
