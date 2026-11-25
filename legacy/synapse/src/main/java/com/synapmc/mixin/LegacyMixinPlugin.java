package com.synapmc.mixin;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;


public final class LegacyMixinPlugin implements IMixinConfigPlugin {
    public static boolean supportsScoreboardText(String minecraftVersion) {
        return "1.20.1".equals(minecraftVersion) || modernSidebar(minecraftVersion);
    }

    private static boolean modernSidebar(String version) { return "1.21".equals(version) || "1.21.1".equals(version); }

    public static boolean supportsMixin(String mixinClassName, String version) {
        if (mixinClassName.endsWith(".Legacy120ScoreboardsMixin") || mixinClassName.endsWith(".Legacy120SidebarMixin"))
            return "1.20.1".equals(version);
        if (mixinClassName.endsWith(".ScoreboardsMixin") || mixinClassName.endsWith(".SidebarMixin")) return modernSidebar(version);
        if (mixinClassName.endsWith(".ItemTooltipMixin") || mixinClassName.endsWith(".HandledScreenTooltipMixin")) return supportsScoreboardText(version);
        if (mixinClassName.endsWith(".ItemStackTextureMixin") || mixinClassName.endsWith(".ItemTextureRenderMixin")
                || mixinClassName.endsWith(".ItemHelperTextureMixin") || mixinClassName.endsWith(".ItemTexturePackMixin")
                || mixinClassName.endsWith(".LegacyItemTextureLoaderMixin") || mixinClassName.endsWith(".LegacyItemTextureModelMixin"))
            return modernSidebar(version);
        return true;
    }

    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return FabricLoader.getInstance().getModContainer("minecraft")
            .map(mod -> supportsMixin(mixinClassName, mod.getMetadata().getVersion().getFriendlyString()))
            .orElseGet(() -> supportsMixin(mixinClassName, ""));
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
