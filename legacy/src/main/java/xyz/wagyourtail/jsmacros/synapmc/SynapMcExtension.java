package xyz.wagyourtail.jsmacros.synapmc;

import com.google.common.collect.Sets;
import com.synapmc.ICoreAccess;
import com.synapmc.SynapMcShared;
import com.synapmc.library.FPort;
import com.synapmc.repo.RepoManager;
import com.synapmc.server.BridgeServer;
import xyz.wagyourtail.jsmacros.core.Core;
import xyz.wagyourtail.jsmacros.core.extensions.Extension;
import xyz.wagyourtail.jsmacros.core.language.BaseLanguage;
import xyz.wagyourtail.jsmacros.core.language.BaseWrappedException;
import xyz.wagyourtail.jsmacros.core.library.BaseLibrary;
import xyz.wagyourtail.jsmacros.synapmc.config.SynapMcConfig;
import xyz.wagyourtail.jsmacros.synapmc.library.FSynapMc;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;

public class SynapMcExtension implements Extension, ICoreAccess {

    @Override
    public String getLanguageImplName() {
        return "synapmc";
    }

    @Override
    public void init() {
        try {
            Core.getInstance().config.addOptions("synapmc", SynapMcConfig.class);
        } catch (Exception e) {
            throw new RuntimeException("[SynapMc] Failed to register config", e);
        }

        SynapMcConfig cfg = Core.getInstance().config.getOptions(SynapMcConfig.class);
        String os = System.getProperty("os.name", "").toLowerCase();
        File roamingDir;
        if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            roamingDir = new File(appdata != null ? appdata : System.getProperty("user.home"), ".jsMacros");
        } else {
            roamingDir = new File(System.getProperty("user.home"), ".jsMacros");
        }
        roamingDir.mkdirs();

        SynapMcShared.core = this;
        SynapMcShared.roamingDir = roamingDir;
        SynapMcShared.initGlobalConfig();
        SynapMcShared.mergeGlobalConfig(cfg.unifyFolders, cfg.pathTags);
        Core.getInstance().config.saveConfig();

        createDirs(roamingDir);
        RepoManager.getInstance().init(roamingDir);

        if (cfg.unifyFolders != null && !cfg.unifyFolders.isEmpty()) {
            for (Map.Entry<String, String> entry : cfg.unifyFolders.entrySet()) {
                createUnifiedJunction(roamingDir, entry.getKey(), entry.getValue());
            }
        }

        if (cfg.loadEnabled) {
            File loadDir = resolveDir(cfg.loadDirectory, roamingDir, "scripts/load");
            if (loadDir.isDirectory()) {
                runLoadScripts(loadDir, cfg);
            }
        }

        if (cfg.tcpPortEnabled) {
            SynapMcShared.server = new BridgeServer(cfg.tcpPort, cfg.tcpPortPassword);
            try {
                SynapMcShared.server.start();
            } catch (Exception e) {
                SynapMcShared.error("[SynapMc] Failed to start TCP port on port " + cfg.tcpPort + ": " + e.getMessage());
            }
        }
    }

    @Override
    public byte[] httpGet(String url) throws IOException {
        URLConnection conn = new URL(url).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);
        try (InputStream in = conn.getInputStream()) {
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) != -1) buf.write(chunk, 0, n);
            return buf.toByteArray();
        }
    }

    @Override
    public String httpGetText(String url) throws IOException {
        return new String(httpGet(url), StandardCharsets.UTF_8);
    }

    @Override
    public void exec(String lang, String script, File file, Consumer<Throwable> errHandler) {
        Core.getInstance().exec(lang, script, file, null, null, ex -> errHandler.accept(ex));
    }

    @Override
    public File getMacroFolder() {
        return Core.getInstance().config.macroFolder;
    }

    @Override
    public File getMinecraftDir() {
        File dir = findMinecraftDir();
        return dir != null ? dir : new File(System.getProperty("user.dir"));
    }

    @Override
    public Object getProfile() throws Exception {
        Core<?, ?> core = Core.getInstance();
        try {
            Method m = core.getClass().getMethod("getProfile");
            return m.invoke(core);
        } catch (NoSuchMethodException e) {
            return core.getClass().getField("profile").get(core);
        }
    }

    @Override
    public boolean hasExtensionForFile(File f) {
        return Core.getInstance().extensions.getExtensionForFile(f) != null;
    }

    @Override
    public boolean isAutoRegisterTriggers() {
        return Core.getInstance().config.getOptions(SynapMcConfig.class).autoRegisterTriggers;
    }

    @Override
    public boolean isSilent() {
        return Core.getInstance().config.getOptions(SynapMcConfig.class).silent;
    }

    @Override
    public boolean isDebug() {
        return Core.getInstance().config.getOptions(SynapMcConfig.class).debug;
    }

    @Override
    public boolean isClientInfoPreAuth() {
        return Core.getInstance().config.getOptions(SynapMcConfig.class).clientInfoPreAuth;
    }

    @Override
    public Map<String, Object> getClientInfo() {
        return buildClientInfo();
    }

    @Override
    public int getTcpPort() {
        return Core.getInstance().config.getOptions(SynapMcConfig.class).tcpPort;
    }

    @Override
    public String getTcpPortPassword() {
        return Core.getInstance().config.getOptions(SynapMcConfig.class).tcpPortPassword;
    }

    @Override
    public Map<String, String> getConfiguredRepos() {
        return Core.getInstance().config.getOptions(SynapMcConfig.class).repos;
    }

    @Override
    public void addConfigRepo(String name, String url) {
        SynapMcConfig cfg = Core.getInstance().config.getOptions(SynapMcConfig.class);
        if (!cfg.repos.containsKey(name)) {
            cfg.repos.put(name, url);
            Core.getInstance().config.saveConfig();
        }
    }

    @Override
    public void removeConfigRepo(String name) {
        Core.getInstance().config.getOptions(SynapMcConfig.class).repos.remove(name);
        Core.getInstance().config.saveConfig();
    }

    @Override
    public void saveConfig() {
        Core.getInstance().config.saveConfig();
    }

    @Override
    public Map<String, String> getUnifyFolders() {
        return Core.getInstance().config.getOptions(SynapMcConfig.class).unifyFolders;
    }

    @Override
    public Map<String, String> getPathTags() {
        return Core.getInstance().config.getOptions(SynapMcConfig.class).pathTags;
    }

    @Override
    public String getScriptTriggerClassName() {
        return "xyz.wagyourtail.jsmacros.core.config.ScriptTrigger";
    }

    @Override
    public int getPriority() {
        return -1;
    }

    @Override
    public ExtMatch extensionMatch(File file) {
        return ExtMatch.NOT_MATCH;
    }

    @Override
    public String defaultFileExtension() {
        return "";
    }

    @Override
    public BaseLanguage<?, ?> getLanguage(Core<?, ?> runner) {
        return null;
    }

    @Override
    public Set<Class<? extends BaseLibrary>> getLibraries() {
        return Sets.newHashSet(FSynapMc.class);
    }

    @Override
    public BaseWrappedException<?> wrapException(Throwable ex) {
        if (SynapMcShared.server != null && SynapMcShared.server.hasClients()) {
            String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            SynapMcShared.server.broadcast(BridgeServer.errorJson(message, null, -1));
        }
        return null;
    }

    @Override
    public boolean isGuestObject(Object o) {
        return false;
    }

    private void createDirs(File roamingDir) {
        String[] dirs = {
            "scripts/library", "scripts/library/main",
            "scripts/load", "scripts/macros",
            "scripts/plugins", "projects", "logs", "assets",
            "assets/images", "assets/sounds", "repos", "synapmc"
        };
        for (String rel : dirs) {
            new File(roamingDir, rel).mkdirs();
        }
    }

    private void createUnifiedJunction(File roamingDir, String junctionName, String roamingRelPath) {
        File target = new File(roamingDir, roamingRelPath);
        target.mkdirs();
        File instanceMacros = Core.getInstance().config.macroFolder;
        if (instanceMacros == null) return;

        File junction = new File(instanceMacros, junctionName);
        if (junction.exists()) return;

        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("cmd", "/c", "mklink", "/J",
                    junction.getAbsolutePath(), target.getAbsolutePath());
            } else {
                pb = new ProcessBuilder("ln", "-s",
                    target.getAbsolutePath(), junction.getAbsolutePath());
            }
            pb.redirectErrorStream(true);
            pb.start().waitFor();
        } catch (Exception e) {
            SynapMcShared.error("[SynapMc] Could not create junction '" + junctionName + "': " + e.getMessage());
        }
    }

    private void runLoadScripts(File loadDir, SynapMcConfig cfg) {
        for (File f : sortedFiles(loadDir)) {
            if (f.isFile() && !f.getName().startsWith("_")) {
                String lang = detectLang(f);
                if (lang != null) execLoadScript(f, lang, cfg);
            }
        }
    }

    private void execLoadScript(File file, String lang, SynapMcConfig cfg) {
        if (Core.getInstance().extensions.getExtensionForFile(file) == null) {
            SynapMcShared.error("[SynapMc] No language extension found for " + file.getName());
            return;
        }
        String content;
        try {
            content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            if (!cfg.silent) writeLog(file, e);
            return;
        }
        Core.getInstance().exec(lang, content, file, null, null, ex -> {
            if (!cfg.silent) writeLog(file, ex);
            if (SynapMcShared.server != null && SynapMcShared.server.hasClients()) {
                SynapMcShared.server.broadcast(BridgeServer.errorJson(
                    ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName(),
                    file.getName(), -1));
            }
            if (!cfg.silent) {
                if (cfg.debug) {
                    SynapMcShared.error("[SynapMc] Error in " + file.getName() + ": " + ex);
                } else {
                    SynapMcShared.error("[SynapMc] \u00a7c" + file.getName() + " failed to load.");
                }
            }
        });
    }

    private void writeLog(File script, Throwable ex) {
        try {
            File log = new File(SynapMcShared.roamingDir, "logs/synapmc.log");
            log.getParentFile().mkdirs();
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            String entry = "[" + timestamp + "] Error in " + script.getName() + ": " + ex + "\n";
            Files.write(log.toPath(), entry.getBytes(StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException ignored) {}
    }

    private File resolveDir(String configured, File roamingDir, String defaultRel) {
        if (configured != null && !configured.isEmpty()) return new File(configured);
        return new File(roamingDir, defaultRel);
    }

    private File findMinecraftDir() {
        Object mc = callStatic("net.minecraft.client.MinecraftClient", "getInstance");
        if (mc == null) mc = callStatic("net.minecraft.class_310", "method_1551");
        if (mc == null) return null;
        Object dir = readField(mc, "runDirectory");
        if (dir == null) dir = readField(mc, "field_1697");
        return dir instanceof File ? (File) dir : null;
    }

    private Map<String, Object> buildClientInfo() {
        Map<String, Object> info = new LinkedHashMap<String, Object>();
        info.put("version", getClientVersion());
        Object mc = callStatic("net.minecraft.client.MinecraftClient", "getInstance");
        if (mc == null) mc = callStatic("net.minecraft.class_310", "method_1551");
        Object session = mc != null ? call(mc, "getSession") : null;
        if (session == null && mc != null) session = call(mc, "method_1548");
        String name = session != null ? stringValue(call(session, "getUsername")) : null;
        if (name == null && session != null) name = stringValue(call(session, "method_1676"));
        String uuid = session != null ? stringValue(call(session, "getUuid")) : null;
        if (uuid == null && session != null) uuid = stringValue(call(session, "method_44717"));
        if (name != null) info.put("name", name);
        if (uuid != null) {
            info.put("uuid", uuid);
            info.put("headUrl", "https://avatars.oc.tc/avatars/" + uuid.replace("-", "") + "?overlay");
        }
        return info;
    }

    private String getClientVersion() {
        try {
            Object sharedConstants = callStatic("net.minecraft.SharedConstants", "getGameVersion");
            if (sharedConstants == null) sharedConstants = callStatic("net.minecraft.class_155", "method_16673");
            String name = sharedConstants != null ? stringValue(call(sharedConstants, "getName")) : null;
            if (name == null && sharedConstants != null) name = stringValue(call(sharedConstants, "method_48019"));
            return name != null ? name : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private Object call(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value != null ? value.toString() : null;
    }

    private Object callStatic(String className, String methodName) {
        try {
            Class<?> type = Class.forName(className);
            return type.getMethod(methodName).invoke(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Object readField(Object target, String fieldName) {
        try {
            java.lang.reflect.Field field = target.getClass().getField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception ignored) {
            try {
                java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    private String detectLang(File f) {
        String name = f.getName();
        if (name.endsWith(".lua")) return "lua";
        if (name.endsWith(".js"))  return "js";
        if (name.endsWith(".py"))  return "py";
        return null;
    }

    private File[] sortedFiles(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return new File[0];
        Arrays.sort(files, Comparator.comparing(File::getName));
        return files;
    }
}
