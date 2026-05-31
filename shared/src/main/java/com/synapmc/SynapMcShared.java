package com.synapmc;

import com.synapmc.server.BridgeServer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Map;


public final class SynapMcShared {

    private SynapMcShared() {}

    public static ICoreAccess core;
    public static File roamingDir;
    public static BridgeServer server;
    public static SynapMcGlobalConfig globalConfig;

    public static void initGlobalConfig() {
        globalConfig = SynapMcGlobalConfig.load(roamingDir);
        if (globalConfig != null) {
            applyRuntimePathTags(globalConfig.pathTags);
            globalConfig.save(roamingDir);
        }
    }

    public static File resolvePath(String path) {
        if (path == null || path.isEmpty()) return roamingDir;
        if (path.startsWith("@")) {
            int slash = firstSlash(path);
            String tag = slash == -1 ? path.substring(1) : path.substring(1, slash);
            String rest = slash == -1 ? "" : path.substring(slash + 1);
            String base = null;
            if (core != null && core.getPathTags() != null) {
                base = core.getPathTags().get(tag);
            }
            if ((base == null || base.isEmpty()) && globalConfig != null && globalConfig.pathTags != null) {
                base = globalConfig.pathTags.get(tag);
            }
            if (base != null && !base.isEmpty()) {
                return new File(resolveBase(base), rest);
            }
            return new File(roamingDir, tag + (rest.isEmpty() ? "" : File.separator + rest));
        }
        return new File(roamingDir, path);
    }

    public static String downloadTo(String url, String savePath, boolean overwrite) throws IOException {
        return downloadTo(url, savePath, null, overwrite);
    }

    public static String downloadTo(String url, String savePath, String filename, boolean overwrite) throws IOException {
        String finalName = filename != null && !filename.isEmpty() ? filename : filenameFromUrl(url);
        File dest = new File(resolvePath(savePath), finalName);
        if (!overwrite && dest.exists()) return null;
        byte[] bytes = core.httpGet(url);
        if (bytes == null || bytes.length == 0) return null;
        dest.getParentFile().mkdirs();
        Files.write(dest.toPath(), bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return dest.getAbsolutePath();
    }

    public static void info(String message) {
        if (core == null || !core.isSilent()) System.out.println(message);
    }

    public static void error(String message) {
        if (core == null || !core.isSilent()) System.err.println(message);
    }

    private static File resolveBase(String base) {
        if (base.startsWith("@")) return resolvePath(base);
        File f = new File(base);
        if (f.isAbsolute()) return f;
        return new File(roamingDir, base);
    }

    private static int firstSlash(String path) {
        int a = path.indexOf('/');
        int b = path.indexOf('\\');
        if (a == -1) return b;
        if (b == -1) return a;
        return Math.min(a, b);
    }

    private static String filenameFromUrl(String url) {
        String clean = url == null ? "" : url.split("\\?")[0];
        int slash = clean.lastIndexOf('/');
        String name = slash == -1 ? clean : clean.substring(slash + 1);
        return name.isEmpty() ? "download.bin" : name;
    }

    public static void mergeGlobalConfig(Map<String, String> configUnifyFolders, Map<String, String> configPathTags) {
        if (globalConfig == null) initGlobalConfig();
        applyRuntimePathTags(globalConfig.pathTags);
        applyRuntimePathTags(configPathTags);
        merge(configUnifyFolders, globalConfig.unifyFolders);
        merge(globalConfig.unifyFolders, configUnifyFolders);
        merge(configPathTags, globalConfig.pathTags);
        merge(globalConfig.pathTags, configPathTags);
        applyRuntimePathTags(globalConfig.pathTags);
        applyRuntimePathTags(configPathTags);
        globalConfig.save(roamingDir);
    }

    private static void applyRuntimePathTags(Map<String, String> tags) {
        if (tags == null || core == null) return;
        File minecraft = core.getMinecraftDir();
        if (minecraft == null) return;
        File mods = new File(minecraft, "mods");
        tags.put("minecraft", minecraft.getAbsolutePath());
        tags.put("mods", mods.getAbsolutePath());
        tags.put("mod", mods.getAbsolutePath());
    }

    private static void merge(Map<String, String> from, Map<String, String> to) {
        if (from == null || to == null) return;
        for (Map.Entry<String, String> entry : from.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && !to.containsKey(entry.getKey())) {
                to.put(entry.getKey(), entry.getValue());
            }
        }
    }
}
