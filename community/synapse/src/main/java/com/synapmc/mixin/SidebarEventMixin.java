package com.synapmc.mixin;

import com.synapmc.CommunitySidebarSnapshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;


@Mixin(targets = "net.minecraft.class_329", remap = false)
public abstract class SidebarEventMixin {
    @Unique private final CommunitySidebarSnapshot synapmc$sidebarSnapshot = new CommunitySidebarSnapshot();

    @ModifyArgs(method = "method_55803", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/class_329;method_1757(Lnet/minecraft/class_332;Lnet/minecraft/class_266;)V"), require = 1)
    private void synapmc$prepareSidebarEvent(Args args) {
        synapmc$sidebarSnapshot.prepare(this, args.get(1));
    }
}
