package xyz.wagyourtail.jsmacros.synapmc;

import com.synapmc.ICoreAccess;
import com.synapmc.MinecraftAccess;
import com.synapmc.LanguageAccess;
import com.synapmc.SynapMcShared;
import com.synapmc.SynapMcSettingsUi;
import com.synapmc.server.BridgeServer;
import xyz.wagyourtail.jsmacros.core.Core;
import xyz.wagyourtail.jsmacros.core.extensions.Extension;
import xyz.wagyourtail.jsmacros.core.language.BaseLanguage;
import xyz.wagyourtail.jsmacros.core.language.BaseWrappedException;
import xyz.wagyourtail.jsmacros.core.library.BaseLibrary;
import xyz.wagyourtail.jsmacros.synapmc.library.FSynapMc;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class SynapMcExtension implements Extension, ICoreAccess {
    @Override public String getLanguageImplName() { return "synapmc"; }
    @Override public void init() {
        SynapMcShared.init(this);
        try { Core.getInstance().config.addOptions("synapmc", SynapMcOptions.class); }
        catch (Exception error) { SynapMcShared.error("[SynapMc] Could not register settings: " + error); }
    }
    @Override public Map<String, String> getTranslations(String language) {
        return SynapMcSettingsUi.translations(language);
    }
    @Override public int getPriority() { return -1; }
    @Override public ExtMatch extensionMatch(File file) { return ExtMatch.NOT_MATCH; }
    @Override public String defaultFileExtension() { return ""; }
    @Override public BaseLanguage<?, ?> getLanguage(Core<?, ?> runner) { return null; }
    @Override public Set<Class<? extends BaseLibrary>> getLibraries() {
        return new LinkedHashSet<Class<? extends BaseLibrary>>(Arrays.<Class<? extends BaseLibrary>>asList(
                FSynapMc.class, FSynapMc.Synapse.class, FSynapMc.Client.class, FSynapMc.Tag.class, FSynapMc.Editor.class,
                FSynapMc.Extra.class, FSynapMc.Patch.class));
    }
    @Override public BaseWrappedException<?> wrapException(Throwable error) {
        if (SynapMcShared.server != null) SynapMcShared.server.broadcast(BridgeServer.errorJson(error.toString(), null, -1));
        return null;
    }
    @Override public boolean isGuestObject(Object value) { return false; }

    @Override public void exec(String language, String script, File file, Consumer<Throwable> onError) {
        Core.getInstance().exec(language, script, file, null, null, onError);
    }
    @Override public File getMacroFolder() { return Core.getInstance().config.macroFolder; }
    @Override public File getExtensionFolder() { return new File(Core.getInstance().config.configFolder, "LanguageExtensions"); }
    @Override public File getMinecraftDir() { return MinecraftAccess.gameDirectory(); }
    @Override public File captureMinecraftScreenshot(String filename) throws Exception { return MinecraftAccess.screenshot(filename); }
    @Override public Map<String, Object> getClientInfo() { return MinecraftAccess.clientInfo(); }
    @Override public boolean isClientInfoPreAuth() { return SynapMcShared.config.clientInfoPreAuth; }
    @Override public Object getJsMacrosConfigManager() { return Core.getInstance().config; }
    @Override public String getScriptTriggerClassName() { return "xyz.wagyourtail.jsmacros.core.config.ScriptTrigger"; }
    @Override public Object getProfile() { return Core.getInstance().profile; }
    @Override public Object getServices() { return Core.getInstance().services; }
    @Override public String getJsVersion() { return "legacy"; }
    @Override public String getLanguageForFile(File file) {
        Extension extension = Core.getInstance().extensions.getExtensionForFile(file);
        return extension == null ? null : extension.getLanguageImplName();
    }
    @Override public Map<String, String> getAvailableLanguages() { return LanguageAccess.available(Core.getInstance().extensions); }
    @Override public Object createScriptDropScreen() {
        try {
            Class.forName("xyz.wagyourtail.jsmacros.client.api.classes.render.ScriptScreen");
            return Class.forName("xyz.wagyourtail.jsmacros.synapmc.ScriptDropScreenModern")
                    .getConstructor().newInstance();
        } catch (ClassNotFoundException legacyApi) {
            return new ScriptDropScreen();
        } catch (Exception error) {
            throw new IllegalStateException("Could not create script importer screen", error);
        }
    }
}
