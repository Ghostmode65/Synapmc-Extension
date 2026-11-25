package com.synapmc.mixin;

import com.synapmc.Legacy120SidebarSession;
import java.util.Collection;
import net.minecraft.class_2561;
import net.minecraft.class_266;
import net.minecraft.class_267;
import net.minecraft.class_269;
import net.minecraft.class_270;
import net.minecraft.class_5250;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Supplies event-edited rows to the original 1.20.1 renderer, preserving its layout. */
@Mixin(targets = "net.minecraft.class_329", remap = false)
public abstract class Legacy120SidebarMixin {
    @Unique private final Legacy120SidebarSession synapmc$sidebar120 = new Legacy120SidebarSession();
    @Unique private boolean synapmc$hasSidebar120;
    @Unique private class_267 synapmc$currentScore120;

    @Inject(method = "method_1753(Lnet/minecraft/class_332;F)V", at = @At("HEAD"), remap = false)
    private void synapmc$begin120(CallbackInfo ci) { synapmc$hasSidebar120 = false; }

    @Inject(method = "method_1753(Lnet/minecraft/class_332;F)V", at = @At("RETURN"), remap = false)
    private void synapmc$end120(CallbackInfo ci) {
        if (!synapmc$hasSidebar120) synapmc$sidebar120.clear();
        synapmc$currentScore120 = null;
    }

    @Inject(method = "method_1757", at = @At("HEAD"), cancellable = true, remap = false)
    private void synapmc$prepare120(@Coerce Object context, class_266 objective, CallbackInfo ci) {
        synapmc$hasSidebar120 = true; synapmc$sidebar120.prepare(objective);
        if (synapmc$sidebar120.title() == null) ci.cancel();
    }

    @Redirect(method = "method_1757", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/class_269;method_1184(Lnet/minecraft/class_266;)Ljava/util/Collection;"), remap = false)
    private Collection<class_267> synapmc$scores120(class_269 board, class_266 objective) { return synapmc$sidebar120.scores(); }

    @Redirect(method = "method_1757", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/class_267;method_1129()Ljava/lang/String;"), remap = false)
    private String synapmc$owner120(class_267 score) { synapmc$currentScore120 = score; return score.method_1129(); }

    @Redirect(method = "method_1757", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/class_268;method_1142(Lnet/minecraft/class_270;Lnet/minecraft/class_2561;)Lnet/minecraft/class_5250;"), remap = false)
    private class_5250 synapmc$text120(class_270 team, class_2561 text) {
        return synapmc$sidebar120.text(synapmc$currentScore120).method_27661();
    }

    @Redirect(method = "method_1757", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/class_266;method_1114()Lnet/minecraft/class_2561;"), remap = false)
    private class_2561 synapmc$title120(class_266 objective) { return synapmc$sidebar120.title(); }
}
