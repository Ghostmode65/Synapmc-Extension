package com.synapmc.mixin;

import com.synapmc.TooltipTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(targets = "@CORE_PACKAGE@.client.api.library.impl.FHud", remap = false)
public abstract class HudMixin {
    // Object keeps older Legacy cores, which lack Alignable, loadable. Non-null results implement it.
    @Unique public Object getLoreWindow() { return TooltipTracker.get(); }
}
