package com.synapmc.mixin;

import com.synapmc.LoreWindow;
import com.synapmc.ItemTooltipContext;
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
public abstract class ItemTooltipMixin implements ItemTooltipContext {
    @Unique private int synapmc$itemTooltipDepth;
    @Unique private final Deque<TooltipRenderSession> synapmc$tooltipSessions = new ArrayDeque<>();
    @Shadow public abstract int method_51421();
    @Shadow public abstract int method_51443();

    @Override @Unique public void synapmc$enterItemTooltip() { synapmc$itemTooltipDepth++; }
    @Override @Unique public void synapmc$leaveItemTooltip() { synapmc$itemTooltipDepth--; }

    @Inject(method = "method_51446", at = @At("HEAD"), require = 1)
    private void synapmc$beginItemTooltip(CallbackInfo ci) { synapmc$enterItemTooltip(); }
    @Inject(method = "method_51446", at = @At("RETURN"), require = 1)
    private void synapmc$endItemTooltip(CallbackInfo ci) { synapmc$leaveItemTooltip(); }

    @Inject(method = "method_51435", at = @At("HEAD"), require = 1)
    private void synapmc$beginTooltipSession(@Coerce Object font, java.util.List<?> components,
            int mouseX, int mouseY, @Coerce Object positioner, CallbackInfo ci) {
        synapmc$tooltipSessions.push(new TooltipRenderSession(this, mouseX, mouseY));
    }
    @Inject(method = "method_51435", at = @At("RETURN"), require = 1)
    private void synapmc$drawTooltipElements(CallbackInfo ci) { synapmc$tooltipSessions.pop().finish(); }


    @ModifyArgs(method = "method_51743", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/class_8002;method_47946(Lnet/minecraft/class_332;IIIII)V"), require = 1)
    private void synapmc$captureTooltipBounds(Args args) {

        LoreWindow window = synapmc$itemTooltipDepth > 0 ? new LoreWindow(
            (int) args.get(1) - 4, (int) args.get(2) - 4,
            (int) args.get(3) + 8, (int) args.get(4) + 8, method_51421(), method_51443()) : null;
        if (window != null) synapmc$tooltipSessions.peek().begin(window);
    }
}
