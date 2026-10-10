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

/** Minecraft 1.21/1.21.1 renders item tooltips immediately. */
@Mixin(targets = "net.minecraft.class_332", remap = false)
public abstract class ItemTooltipMixin {
    @Unique private boolean synapmc$itemTooltip;
    @Unique private final Deque<TooltipRenderSession> synapmc$tooltipSessions = new ArrayDeque<>();
    @Shadow public abstract int method_51421();
    @Shadow public abstract int method_51443();

    @Inject(method = "method_51446", at = @At("HEAD"), require = 1)
    private void synapmc$beginItemTooltip(CallbackInfo ci) { synapmc$itemTooltip = true; }
    @Inject(method = "method_51446", at = @At("RETURN"), require = 1)
    private void synapmc$endItemTooltip(CallbackInfo ci) { synapmc$itemTooltip = false; }

    @Inject(method = "method_51435", at = @At("HEAD"), require = 1)
    private void synapmc$beginTooltipSession(@Coerce Object font, java.util.List<?> components,
            int mouseX, int mouseY, @Coerce Object positioner, CallbackInfo ci) {
        synapmc$tooltipSessions.push(new TooltipRenderSession(this, mouseX, mouseY));
    }
    @Inject(method = "method_51435", at = @At("RETURN"), require = 1)
    private void synapmc$drawTooltipElements(CallbackInfo ci) { synapmc$tooltipSessions.pop().finish(); }

    // The immediate draw method wraps background drawing in this flush lambda.
    @ModifyArgs(method = "method_51743", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/class_8002;method_47946(Lnet/minecraft/class_332;IIIII)V"), require = 1)
    private void synapmc$captureTooltipBounds(Args args) {
        // The old gradient renderer extends four pixels past the content on each side.
        LoreWindow window = synapmc$itemTooltip ? new LoreWindow(
            (int) args.get(1) - 4, (int) args.get(2) - 4,
            (int) args.get(3) + 8, (int) args.get(4) + 8, method_51421(), method_51443()) : null;
        TooltipTracker.capture(window);
        if (window != null) synapmc$tooltipSessions.peek().begin(window);
    }
}
