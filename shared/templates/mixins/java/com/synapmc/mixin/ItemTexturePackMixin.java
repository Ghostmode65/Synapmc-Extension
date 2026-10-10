package com.synapmc.mixin;

import com.synapmc.NativeItemTextures;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "@PACK_MANAGER@", remap = false)
public abstract class ItemTexturePackMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, remap = false)
    private static List<?> synapmc$texturePack(List<?> packs) { return NativeItemTextures.appendPack(packs); }
}
