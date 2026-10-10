package com.synapmc.mixin;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Scoreboard and tooltip capture use the native 1.21/1.21.1 APIs. */
public final class LegacyMixinPlugin implements IMixinConfigPlugin {
    public static boolean supportsScoreboardText(String minecraftVersion) {
        return "1.21".equals(minecraftVersion) || "1.21.1".equals(minecraftVersion);
    }

    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.endsWith(".ScoreboardsMixin") && !mixinClassName.endsWith(".SidebarMixin") && !mixinClassName.endsWith(".ItemTooltipMixin")
                && !mixinClassName.endsWith(".TooltipFrameMixin") && !mixinClassName.endsWith(".ItemStackTextureMixin")
                && !mixinClassName.endsWith(".ItemTextureRenderMixin") && !mixinClassName.endsWith(".ItemHelperTextureMixin")
                && !mixinClassName.endsWith(".ItemTexturePackMixin") && !mixinClassName.endsWith(".LegacyItemTextureLoaderMixin")
                && !mixinClassName.endsWith(".LegacyItemTextureModelMixin")) return true;
        return FabricLoader.getInstance().getModContainer("minecraft")
            .map(mod -> supportsScoreboardText(mod.getMetadata().getVersion().getFriendlyString()))
            .orElse(false);
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
