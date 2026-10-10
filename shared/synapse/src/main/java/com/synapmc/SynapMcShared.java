package com.synapmc;

import com.synapmc.server.BridgeServer;
import com.synapmc.repo.RepoManager;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SynapMcShared {
    private SynapMcShared() {}

    public static volatile ICoreAccess core;
    public static volatile File roamingDir;
    public static volatile SynapMcConfig config;
    public static volatile BridgeServer server;

    public static synchronized void init(ICoreAccess access) {
        core = access;
        String os = System.getProperty("os.name", "").toLowerCase();
        String base = os.contains("win") ? System.getenv("APPDATA") : null;
        if (base == null || base.isEmpty()) base = System.getProperty("user.home");
        roamingDir = new File(base, ".jsmacros");
        config = SynapMcConfig.load(roamingDir);

        for (String directory : new String[] {
                "config", "assets/images", "assets/sounds", "scripts/library",
                "scripts/auto-execute", "scripts/macros", "scripts/projects", "downloads"
        }) {
            new File(roamingDir, directory).mkdirs();
        }
        saveConfig();
        refreshUnifyFolders();
        refreshTcp();
        RepoManager.getInstance().init(roamingDir);
        if (config.autorunEnabled) runAutorun();
        MacroScreenBridge.start();
    }

    public static synchronized void refreshTcp() {
        if (server != null) server.stop();
        server = null;
        if (config == null) return;
        if (config.tcpPortEnabled) {
            try {
                server = new BridgeServer(config.tcpPort, config.tcpPortPassword, config.tcpBindAddress);
                server.start();
            } catch (IOException e) {
                server = null;
                error("[SynapMc] TCP port failed: " + e.getMessage());
            }
        }
    }

    public static boolean saveConfig() {
        if (config == null || roamingDir == null) return false;
        try {
            config.save(roamingDir);
            return true;
        } catch (Exception e) {
            error("[SynapMc] Could not save config: " + e.getMessage());
            return false;
        }
    }

    public static Map<String, String> builtInTags() {
        Map<String, String> tags = new LinkedHashMap<String, String>();
        tags.put("library", new File(roamingDir, "scripts/library").getAbsolutePath());
        tags.put("macros", new File(roamingDir, "scripts/macros").getAbsolutePath());
        File minecraft = core != null ? core.getMinecraftDir() : null;
        if (minecraft != null) {
            tags.put("minecraft", minecraft.getAbsolutePath());
            String mods = new File(minecraft, "mods").getAbsolutePath();
            tags.put("mods", mods);
            tags.put("mod", mods);

        }
        tags.put("desktop", new File(System.getProperty("user.home"), "Desktop").getAbsolutePath());
        return tags;
    }

    public static File resolvePath(String input) {
        if (input == null || input.isEmpty()) return roamingDir;
        if (!input.startsWith("@")) {
            File file = new File(input);
            return file.isAbsolute() ? file : new File(roamingDir, input);
        }
        int slash = input.indexOf('/');
        int backslash = input.indexOf('\\');
        if (slash < 0 || (backslash >= 0 && backslash < slash)) slash = backslash;
        String key = slash < 0 ? input.substring(1) : input.substring(1, slash);
        String rest = slash < 0 ? "" : input.substring(slash + 1);
        String base = builtInTags().get(key);
        if (base == null && config != null && config.tags != null) base = config.tags.get(key);
        if (base == null) throw new IllegalArgumentException("Unknown tag @" + key);
        File directory = new File(base);
        if (!directory.isAbsolute()) directory = new File(roamingDir, base);
        return rest.isEmpty() ? directory : new File(directory, rest);
    }

    public static void info(String message) {
        if (config == null || !config.silent) System.out.println(message);
    }

    public static void error(String message) {
        if (config == null || !config.silent) System.err.println(message);
    }

    public static void notifyDownload(File file) {
        MinecraftAccess.notifyPlayer("Downloaded: " + file.getAbsolutePath());
        info("[SynapMc] Downloaded " + file.getAbsolutePath());
    }

    private static void runAutorun() {
        File dir = new File(roamingDir, "scripts/auto-execute");
        File[] files = dir.listFiles();
        if (files == null) return;
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File file : files) {
            if (!file.isFile() || file.getName().startsWith("_")) continue;
            String name = file.getName().toLowerCase();
            String language = name.endsWith(".js") ? "js" : name.endsWith(".lua") ? "lua"
                    : name.endsWith(".py") ? "py" : name.endsWith(".rb") ? "rb" : null;
            if (language == null) continue;
            try {
                String script = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                core.exec(language, script, file, error -> reportScriptError(file, error));
            } catch (Exception e) {
                reportScriptError(file, e);
            }
        }
    }

    private static void reportScriptError(File file, Throwable failure) {
        error("[SynapMc] Auto-execute failed for " + file.getName() + ": " + failure);
        BridgeServer bridge = server;
        if (bridge != null) bridge.broadcast(BridgeServer.errorJson(failure.toString(), file.getName(), -1));
    }

    public static void refreshUnifyFolders(Map<String, String> previous) {
        if (core.getMacroFolder() == null) return;
        Path root = core.getMacroFolder().toPath().toAbsolutePath().normalize();
        Path shared = roamingDir.toPath().toAbsolutePath().normalize();
        for (Map.Entry<String, String> entry : previous.entrySet()) {
            String name = entry.getKey();
            if (java.util.Objects.equals(entry.getValue(), config.unifyFolders.get(name))) continue;
            if (name == null || name.isEmpty() || name.equals(".") || name.equals("..") || name.contains("/") || name.contains("\\")
                    || entry.getValue() == null || entry.getValue().isBlank()) continue;
            Path link = root.resolve(name);
            try {
                Path target = shared.resolve(entry.getValue()).normalize();
                if (!Files.exists(link, java.nio.file.LinkOption.NOFOLLOW_LINKS)) continue;
                var attributes = Files.readAttributes(link, java.nio.file.attribute.BasicFileAttributes.class,
                        java.nio.file.LinkOption.NOFOLLOW_LINKS);
                boolean matches = attributes.isSymbolicLink()
                        ? link.getParent().resolve(Files.readSymbolicLink(link)).normalize().equals(target)
                        : attributes.isOther() && Files.exists(target) && Files.isSameFile(link, target);
                if (matches) Files.delete(link);
            } catch (Exception error) {
                error("[SynapMc] Could not remove old " + name + " link: " + error.getMessage());
            }
        }
        refreshUnifyFolders();
    }

    public static void refreshUnifyFolders() {
        File macroFolder = core.getMacroFolder();
        if (macroFolder == null || config.unifyFolders == null) return;
        Path macroRoot = macroFolder.toPath().toAbsolutePath().normalize();
        Path roamingRoot = roamingDir.toPath().toAbsolutePath().normalize();
        for (String name : new String[] {"macros", "installed"}) {
            if (config.unifyFolders.containsKey(name)) continue;
            Path link = macroRoot.resolve(name);
            Path target = roamingRoot.resolve(name.equals("macros") ? "scripts/macros" : "scripts/macros/installed");
            try {
                if (Files.exists(link, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                    var attributes = Files.readAttributes(link, java.nio.file.attribute.BasicFileAttributes.class,
                            java.nio.file.LinkOption.NOFOLLOW_LINKS);
                    boolean matches = attributes.isSymbolicLink()
                            ? link.getParent().resolve(Files.readSymbolicLink(link)).normalize().equals(target)
                            : attributes.isOther() && Files.isSameFile(link, target);
                    if (matches)
                        Files.delete(link);
                }
            } catch (IOException e) {
                error("[SynapMc] Could not remove old " + name + " link: " + e.getMessage());
            }
        }
        for (Map.Entry<String, String> entry : config.unifyFolders.entrySet()) {
            String name = entry.getKey();
            String relative = entry.getValue();
            if (name == null || name.isEmpty() || name.contains("/") || name.contains("\\") || name.equals(".") || name.equals("..") || relative == null || relative.isBlank()) continue;
            Path target = roamingRoot.resolve(relative).normalize();
            if (!Path.of(relative).isAbsolute() && !target.startsWith(roamingRoot)) continue;
            Path junction = macroRoot.resolve(name).normalize();
            if (!junction.startsWith(macroRoot)) continue;
            try {
                Files.createDirectories(target);
                if (name.equals("global")) {
                    Files.createDirectories(target.resolve("installed"));
                    Files.createDirectories(target.resolve("imported"));
                }
                if (Files.isSymbolicLink(junction)) {
                    Path current = junction.getParent().resolve(Files.readSymbolicLink(junction)).normalize();
                    if (current.equals(target)) continue;
                    Files.delete(junction);
                } else if (Files.exists(junction, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                    // Preserve ordinary folders; only replace links managed by SynapMc.
                    continue;
                }
                Files.createDirectories(macroRoot);
                if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                    Process process = new ProcessBuilder("cmd", "/c", "mklink", "/J", junction.toString(), target.toString()).start();
                    if (process.waitFor() != 0) error("[SynapMc] Could not link " + name);
                } else {
                    Files.createSymbolicLink(junction, target);
                }
            } catch (Exception e) {
                error("[SynapMc] Could not link " + name + ": " + e.getMessage());
            }
        }
    }
}
