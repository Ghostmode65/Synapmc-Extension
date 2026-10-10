package com.synapmc.mixin;

import com.synapmc.ItemStackTexture;
import @CORE_PACKAGE@.core.helpers.BaseHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Adds getTexture directly to native JsMacros ItemStackHelper objects. */
@Mixin(targets = "@CORE_PACKAGE@.client.api.@HELPER_PACKAGE@.inventory.ItemStackHelper", remap = false)
public abstract class ItemStackTextureMixin extends BaseHelper<Object> {
    protected ItemStackTextureMixin(Object stack) { super(stack); }
    @Unique private ItemStackTexture synapmc$texture;
    @Unique public ItemStackTexture getTexture() {
        if (synapmc$texture == null) synapmc$texture = new ItemStackTexture(() -> base, stack -> base = stack);
        return synapmc$texture;
    }
}
