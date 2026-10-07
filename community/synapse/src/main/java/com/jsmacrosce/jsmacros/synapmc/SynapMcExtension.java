package com.jsmacrosce.jsmacros.synapmc;

import com.jsmacrosce.jsmacros.core.Core;
import com.jsmacrosce.jsmacros.core.extensions.Extension;
import com.jsmacrosce.jsmacros.core.extensions.LanguageExtension;
import com.jsmacrosce.jsmacros.core.extensions.LibraryExtension;
import com.jsmacrosce.jsmacros.core.library.BaseLibrary;
import com.jsmacrosce.jsmacros.synapmc.library.FSynapMc;
import com.synapmc.ICoreAccess;
import com.synapmc.MinecraftAccess;
import com.synapmc.LanguageAccess;
import com.synapmc.SynapMcShared;
import com.synapmc.SynapMcSettingsUi;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class SynapMcExtension implements Extension, LibraryExtension, ICoreAccess {
    private Core<?, ?> core;

    @Override public String getExtensionName() { return "synapmc"; }
    @Override public void init(Core<?, ?> core) {
        this.core = core;
        SynapMcShared.init(this);
        try { core.config.addOptions("synapmc", SynapMcOptions.class); }
        catch (Exception error) { SynapMcShared.error("[SynapMc] Could not register settings: " + error); }
    }
    @Override public Map<String, String> getTranslations(String language) {
        return SynapMcSettingsUi.translations(language);
    }
    @Override public Set<Class<? extends BaseLibrary>> getLibraries() {
        return new LinkedHashSet<Class<? extends BaseLibrary>>(Arrays.<Class<? extends BaseLibrary>>asList(
                FSynapMc.class, FSynapMc.Client.class, FSynapMc.Tag.class));
    }
    @Override public void exec(String language, String script, File file, Consumer<Throwable> onError) {
        core.exec(language, script, file, null, null, onError);
    }
    @Override public File getMacroFolder() { return core.config.macroFolder; }
    @Override public File getExtensionFolder() { return new File(core.config.configFolder, "Extensions"); }
    @Override public File getMinecraftDir() { return MinecraftAccess.gameDirectory(); }
    @Override public File captureMinecraftScreenshot(String filename) throws Exception { return MinecraftAccess.screenshot(filename); }
    @Override public Map<String, Object> getClientInfo() { return MinecraftAccess.clientInfo(); }
    @Override public boolean isClientInfoPreAuth() { return SynapMcShared.config.clientInfoPreAuth; }
    @Override public Object getJsMacrosConfigManager() { return core.config; }
    @Override public String getScriptTriggerClassName() { return "com.jsmacrosce.jsmacros.core.config.ScriptTrigger"; }
    @Override public Object getProfile() { return core.profile; }
    @Override public Object getServices() { return core.services; }
    @Override public String getJsVersion() { return "community"; }
    @Override public String getLanguageForFile(File file) {
        LanguageExtension extension = core.extensions.getExtensionForFile(file);
        return extension == null ? null : extension.getExtensionName();
    }
    @Override public Map<String, String> getAvailableLanguages() { return LanguageAccess.available(core.extensions); }
    @Override public Object createScriptDropScreen() { return new ScriptDropScreen(); }
}
