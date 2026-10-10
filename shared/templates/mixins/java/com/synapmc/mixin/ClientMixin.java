package com.synapmc.mixin;

import com.synapmc.ClientMethods;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Adds real public methods to the existing Client library object. */
@Mixin(targets = "@CORE_PACKAGE@.client.api.library.impl.FClient", remap = false)
public abstract class ClientMixin {
    @Unique public String getOS() { return ClientMethods.getOS(); }
    @Unique public String jsVersion() { return ClientMethods.jsVersion(); }
    @Unique public boolean isFullscreen() { return ClientMethods.isFullscreen(); }
    @Unique public boolean toggleFullscreen(boolean enabled) { return ClientMethods.toggleFullscreen(enabled); }
}
