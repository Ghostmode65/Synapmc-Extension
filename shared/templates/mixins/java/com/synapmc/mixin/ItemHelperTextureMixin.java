package com.synapmc.mixin;

import com.synapmc.ItemTexture;
import @CORE_PACKAGE@.core.helpers.BaseHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(targets = "@CORE_PACKAGE@.client.api.@HELPER_PACKAGE@.inventory.ItemHelper", remap = false)
public abstract class ItemHelperTextureMixin extends BaseHelper<Object> {
    protected ItemHelperTextureMixin(Object item) { super(item); }
    @Unique private ItemTexture synapmc$texture;
    @Unique public ItemTexture getTexture() {
        if (synapmc$texture == null) synapmc$texture = new ItemTexture(base);
        return synapmc$texture;
    }
}
