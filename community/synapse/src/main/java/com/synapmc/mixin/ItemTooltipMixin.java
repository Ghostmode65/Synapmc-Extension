package com.synapmc.mixin;

import com.synapmc.LoreWindow;
import com.synapmc.TooltipTracker;
import com.synapmc.TooltipRenderSession;
import java.util.ArrayDeque;
import java.util.Deque;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(targets = "net.minecraft.class_332", remap = false)
public abstract class ItemTooltipMixin {
    @Unique private boolean synapmc$itemTooltip;
    @Unique private boolean synapmc$deferredItemTooltip;
    @Unique private boolean synapmc$renderingItemTooltip;
    @Unique private final Deque<TooltipRenderSession> synapmc$tooltipSessions = new ArrayDeque<>();
    @Shadow public abstract int method_51421();
    @Shadow public abstract int method_51443();

    @Inject(method = "method_51446", at = @At("HEAD"), require = 1)
    private void synapmc$beginItemTooltip(CallbackInfo ci) { synapmc$itemTooltip = true; }
    @Inject(method = "method_51446", at = @At("RETURN"), require = 1)
    private void synapmc$endItemTooltip(CallbackInfo ci) { synapmc$itemTooltip = false; }

    // Only tag an accepted tooltip, so an ignored request cannot replace its item/non-item origin.
    @Inject(method = "method_71273", at = @At(value = "FIELD",
        target = "Lnet/minecraft/class_332;field_60305:Ljava/lang/Runnable;",
        opcode = 181, shift = At.Shift.AFTER), require = 1)
    private void synapmc$tagDeferredTooltip(CallbackInfo ci) { synapmc$deferredItemTooltip = synapmc$itemTooltip; }

    @Inject(method = "method_71277", at = @At("HEAD"), require = 1)
    private void synapmc$beginDeferredTooltip(CallbackInfo ci) { synapmc$renderingItemTooltip = synapmc$deferredItemTooltip; }
    @Inject(method = "method_71277", at = @At("RETURN"), require = 1)
    private void synapmc$endDeferredTooltip(CallbackInfo ci) { synapmc$renderingItemTooltip = false; }

    @Inject(method = "method_51435", at = @At("HEAD"), require = 1)
    private void synapmc$beginTooltipSession(@Coerce Object font, java.util.List<?> components,
            int mouseX, int mouseY, @Coerce Object positioner, @Coerce Object style, CallbackInfo ci) {
        synapmc$tooltipSessions.push(new TooltipRenderSession(this, mouseX, mouseY));
    }
    @Inject(method = "method_51435", at = @At("RETURN"), require = 1)
    private void synapmc$drawTooltipElements(CallbackInfo ci) { synapmc$tooltipSessions.pop().finish(); }

    @ModifyArgs(method = "method_51435", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/class_8002;method_47946(Lnet/minecraft/class_332;IIIILnet/minecraft/class_2960;)V"), require = 1)
    private void synapmc$captureTooltipBounds(Args args) {
        // Include the background's three-pixel padding; exclude the sprite's transparent nine-pixel margin.
        LoreWindow window = synapmc$renderingItemTooltip ? new LoreWindow(
            (int) args.get(1) - 3, (int) args.get(2) - 3,
            (int) args.get(3) + 6, (int) args.get(4) + 6, method_51421(), method_51443()) : null;
        TooltipTracker.capture(window);
        if (window != null) synapmc$tooltipSessions.peek().begin(window);
    }
}
