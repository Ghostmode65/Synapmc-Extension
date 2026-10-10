package com.synapmc.mixin;

import com.synapmc.SidebarRenderSession;
import java.util.function.IntFunction;
import java.util.stream.Stream;
import @OBJECTIVE_TYPE@;
import @TEXT_TYPE@;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Edits already sorted/decorated display entries before native width measurement and rendering. */
@Mixin(targets = "@SIDEBAR_OWNER@", remap = false)
public abstract class SidebarMixin {
    @Unique private final SidebarRenderSession synapmc$sidebar = new SidebarRenderSession();
    @Unique private boolean synapmc$hasSidebar;

    @Inject(method = "@SIDEBAR_OUTER@", at = @At("HEAD"), remap = false)
    private void synapmc$begin(CallbackInfo ci) { synapmc$hasSidebar = false; }

    @Inject(method = "@SIDEBAR_OUTER@", at = @At("RETURN"), remap = false)
    private void synapmc$end(CallbackInfo ci) { if (!synapmc$hasSidebar) synapmc$sidebar.clear(); }

    @Redirect(method = "@SIDEBAR_METHOD@", at = @At(value = "INVOKE",
        target = "Ljava/util/stream/Stream;toArray(Ljava/util/function/IntFunction;)[Ljava/lang/Object;", remap = false), remap = false)
    private Object[] synapmc$entries(Stream<?> stream, IntFunction<Object[]> factory,
            @Coerce Object context, @OBJECTIVE_SIMPLE@ objective) {
        synapmc$hasSidebar = true;
        return synapmc$sidebar.apply(objective, objective.@OBJECTIVE_TITLE@(), stream.toArray(factory), factory);
    }

    @Redirect(method = "@SIDEBAR_METHOD@", at = @At(value = "INVOKE",
        target = "L@OBJECTIVE_INTERNAL@;@OBJECTIVE_TITLE@()L@TEXT_INTERNAL@;", remap = false), remap = false)
    private @TEXT_SIMPLE@ synapmc$title(@OBJECTIVE_SIMPLE@ objective) { return synapmc$sidebar.title(); }

    @Inject(method = "@SIDEBAR_METHOD@", at = @At(value = "INVOKE",
        target = "L@OBJECTIVE_INTERNAL@;@OBJECTIVE_TITLE@()L@TEXT_INTERNAL@;", remap = false), cancellable = true, remap = false)
    private void synapmc$skipAbsentTitle(CallbackInfo ci) {
        // Canceling the very first update has no previous sidebar to display.
        if (synapmc$sidebar.title() == null) ci.cancel();
    }
}
