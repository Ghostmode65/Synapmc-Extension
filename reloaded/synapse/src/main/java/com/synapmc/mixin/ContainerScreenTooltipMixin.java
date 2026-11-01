package com.synapmc.mixin;

import com.synapmc.ItemTooltipContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.screens.inventory.AbstractContainerScreen", remap = false)
public abstract class ContainerScreenTooltipMixin {
    @Inject(method = "extractTooltip", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;)V"), require = 1)
    private void synapmc$beginInventoryTooltip(@Coerce Object context, int mouseX, int mouseY, CallbackInfo ci) {
        ((ItemTooltipContext) context).synapmc$enterItemTooltip();
    }

    @Inject(method = "extractTooltip", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;)V",
        shift = At.Shift.AFTER), require = 1)
    private void synapmc$endInventoryTooltip(@Coerce Object context, int mouseX, int mouseY, CallbackInfo ci) {
        ((ItemTooltipContext) context).synapmc$leaveItemTooltip();
    }
}
