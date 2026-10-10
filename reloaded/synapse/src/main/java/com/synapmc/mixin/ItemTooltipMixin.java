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

@Mixin(targets = "net.minecraft.client.gui.GuiGraphicsExtractor", remap = false)
public abstract class ItemTooltipMixin {
    @Unique private boolean synapmc$itemTooltip;
    @Unique private boolean synapmc$deferredItemTooltip;
    @Unique private boolean synapmc$renderingItemTooltip;
    @Unique private final Deque<TooltipRenderSession> synapmc$tooltipSessions = new ArrayDeque<>();
    @Shadow public abstract int guiWidth();
    @Shadow public abstract int guiHeight();

    @Inject(method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V",
        at = @At("HEAD"), require = 1)
    private void synapmc$beginItemTooltip(CallbackInfo ci) { synapmc$itemTooltip = true; }
    @Inject(method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V",
        at = @At("RETURN"), require = 1)
    private void synapmc$endItemTooltip(CallbackInfo ci) { synapmc$itemTooltip = false; }

    @Inject(method = "setTooltipForNextFrameInternal", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;deferredTooltip:Ljava/lang/Runnable;",
        opcode = 181, shift = At.Shift.AFTER), require = 1)
    private void synapmc$tagDeferredTooltip(CallbackInfo ci) { synapmc$deferredItemTooltip = synapmc$itemTooltip; }

    @Inject(method = "lambda$setTooltipForNextFrameInternal$0", at = @At("HEAD"), require = 1)
    private void synapmc$beginDeferredTooltip(CallbackInfo ci) { synapmc$renderingItemTooltip = synapmc$deferredItemTooltip; }
    @Inject(method = "lambda$setTooltipForNextFrameInternal$0", at = @At("RETURN"), require = 1)
    private void synapmc$endDeferredTooltip(CallbackInfo ci) { synapmc$renderingItemTooltip = false; }

    @Inject(method = "tooltip", at = @At("HEAD"), require = 1)
    private void synapmc$beginTooltipSession(@Coerce Object font, java.util.List<?> components,
            int mouseX, int mouseY, @Coerce Object positioner, @Coerce Object style, CallbackInfo ci) {
        synapmc$tooltipSessions.push(new TooltipRenderSession(this, mouseX, mouseY));
    }
    @Inject(method = "tooltip", at = @At("RETURN"), require = 1)
    private void synapmc$drawTooltipElements(CallbackInfo ci) { synapmc$tooltipSessions.pop().finish(); }

    @ModifyArgs(method = "tooltip", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/TooltipRenderUtil;extractTooltipBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIILnet/minecraft/resources/Identifier;)V"), require = 1)
    private void synapmc$captureTooltipBounds(Args args) {
        LoreWindow window = synapmc$renderingItemTooltip ? new LoreWindow(
            (int) args.get(1) - 3, (int) args.get(2) - 3,
            (int) args.get(3) + 6, (int) args.get(4) + 6, guiWidth(), guiHeight()) : null;
        TooltipTracker.capture(window);
        if (window != null) synapmc$tooltipSessions.peek().begin(window);
    }
}
