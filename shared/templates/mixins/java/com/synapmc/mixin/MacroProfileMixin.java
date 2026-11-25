package com.synapmc.mixin;

import com.synapmc.MacroScreenBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Leaves the native profile-name header area available for compact shortcuts. */
@Mixin(targets = "@CORE_PACKAGE@.client.gui.screens.MacroScreen", remap = false)
public abstract class MacroProfileMixin implements MacroScreenBridge.HeaderScreen {
    @Unique private final MacroScreenBridge.Header synapmc$header = new MacroScreenBridge.Header();
    @Unique public MacroScreenBridge.Header synapmcHeader() { return synapmc$header; }

    @Inject(method = "@EDITOR_INIT@()V", at = @At("TAIL"), remap = false)
    private void synapmc$attachHeader(CallbackInfo ci) { MacroScreenBridge.attachHeader(this); }

    @Inject(method = "@ELEMENT_RENDER_METHOD@(L@EDITOR_CONTEXT_INTERNAL@;IIF)V", at = @At("HEAD"),
            require = @EDITOR_REQUIRE@, remap = false)
    private void synapmc$animateHeader(CallbackInfo ci) { MacroScreenBridge.renderHeader(this); }

    @Redirect(method = "@ELEMENT_RENDER_METHOD@", at = @At(value = "INVOKE",
            target = "L@CORE_INTERNAL@/client/config/ClientProfile;getCurrentProfileName()Ljava/lang/String;"),
            require = @EDITOR_REQUIRE@, remap = false)
    private String synapmc$profileHeader(@Coerce Object profile) { return ""; }

    // Legacy 1.7 renders through BaseProfile instead of ClientProfile.
    @Redirect(method = "@ELEMENT_RENDER_METHOD@", at = @At(value = "INVOKE",
            target = "L@CORE_INTERNAL@/core/config/BaseProfile;getCurrentProfileName()Ljava/lang/String;"),
            require = 0, remap = false)
    private String synapmc$legacyProfileHeader(@Coerce Object profile) { return ""; }

    @Redirect(method = "@ELEMENT_RENDER_METHOD@", at = @At(value = "INVOKE",
            target = "@MACRO_FILL_TARGET@"), require = @EDITOR_REQUIRE@, remap = false)
    private void synapmc$headerDivider(@EDITOR_CONTEXT@ context, int x1, int y1, int x2, int y2, int color) {
        MacroScreenBridge.fillHeader(this, context, x1, y1, x2, y2, color);
    }
}
