package com.synapmc.mixin;

import com.synapmc.NativeItemTextures;
import @STACK_TYPE@;
import @IDENTIFIER_TYPE@;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "@ITEM_RESOLVER@", remap = false)
public abstract class ItemTextureModelMixin {


    @ModifyVariable(method = "@APPEND_ITEM_LAYERS@", at = @At("STORE"), ordinal = 0, require = 1, remap = false)
    private @IDENTIFIER_SIMPLE@ synapmc$itemModel(@IDENTIFIER_SIMPLE@ model,
            @Coerce Object renderState, @STACK_SIMPLE@ stack) {
        return (@IDENTIFIER_SIMPLE@) NativeItemTextures.modelComponent(stack, model);
    }
}
