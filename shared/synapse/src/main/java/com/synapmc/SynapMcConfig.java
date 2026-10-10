package com.synapmc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SynapMcConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean autorunEnabled = true;
    public boolean silent = false;
    public volatile boolean chatSayEnabled = true;
    public boolean creatorMode = false;
    public boolean tcpPortEnabled = false;
    public int tcpPort = 8765;
    public String tcpPortPassword = "";
    public String tcpBindAddress = "";
    public boolean clientInfoPreAuth = true;
    public boolean logExecuted = true;
    public int downloadMaxMb = 300;
    public String exeDownloadDir = "";
    public String appimageDownloadDir = "";
    public Map<String, String> unifyFolders = new LinkedHashMap<String, String>();
    public Map<String, String> tags = new LinkedHashMap<String, String>();
    public Map<String, String> repos = new LinkedHashMap<String, String>();
    public boolean defaultReposInitialized = false;

    public SynapMcConfig() {
        unifyFolders.put("global", "scripts/macros");
    }

    public static SynapMcConfig load(File roamingDir) {
        Path path = path(roamingDir);
        if (Files.isRegularFile(path)) {
            try {
                SynapMcConfig loaded = GSON.fromJson(new String(Files.readAllBytes(path), StandardCharsets.UTF_8), SynapMcConfig.class);
                if (loaded != null) {
                    loaded.loadSharedMaps(roamingDir);
                    loaded.normalize();
                    return loaded;
                }
            } catch (Exception e) {
                System.err.println("[SynapMc] Could not read config: " + e.getMessage());
            }
        }
        SynapMcConfig defaults = new SynapMcConfig();
        defaults.loadSharedMaps(roamingDir);
        defaults.normalize();
        return defaults;
    }

    public synchronized void save(File roamingDir) throws IOException {
        normalize();
        write(path(roamingDir), this);
        write(new File(roamingDir, "config/repositories.json").toPath(), repos);
        write(new File(roamingDir, "config/unified-folders.json").toPath(), unifyFolders);
    }

    private void loadSharedMaps(File roamingDir) {
        for (String name : new String[] {"repositories", "unified-folders"}) {
            Path file = new File(roamingDir, "config/" + name + ".json").toPath();
            if (!Files.isRegularFile(file)) continue;
            try {
                Map<String, String> values = GSON.fromJson(Files.readString(file),
                        new com.google.gson.reflect.TypeToken<LinkedHashMap<String, String>>() {}.getType());
                if (values == null || values.containsKey(null) || values.containsValue(null))
                    throw new IOException("Expected a map of names to paths or URLs");
                if (name.equals("repositories")) repos = values;
                else unifyFolders = values;
            } catch (Exception error) {
                System.err.println("[SynapMc] Could not read " + file + ": " + error.getMessage());
            }
        }
    }

    public static void write(Path path, Object value) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "synapmc-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(value), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void normalize() {
        if (tcpPort < 1 || tcpPort > 65535) tcpPort = 8765;
        if (tcpPortPassword == null) tcpPortPassword = "";
        if (tcpBindAddress == null) tcpBindAddress = "";
        if (downloadMaxMb < 1 || downloadMaxMb > 4096) downloadMaxMb = 300;
        if (exeDownloadDir == null) exeDownloadDir = "";
        if (appimageDownloadDir == null) appimageDownloadDir = "";
        if (unifyFolders == null) unifyFolders = new LinkedHashMap<String, String>();
        unifyFolders.remove("installed", "scripts/macros/installed");
        if ("scripts/macros".equals(unifyFolders.get("macros"))) {
            unifyFolders.remove("macros");
            unifyFolders.putIfAbsent("global", "scripts/macros");
        }
        unifyFolders.putIfAbsent("global", "scripts/macros");
        if (tags == null) tags = new LinkedHashMap<String, String>();
        if (repos == null) repos = new LinkedHashMap<String, String>();
        // Seed existing installations once, while preserving later removals.
        if (!defaultReposInitialized) {
            if (!repos.containsValue(com.synapmc.repo.RepositoryDefaults.DEFAULT_MANIFEST)) {
                String name = com.synapmc.repo.RepositoryDefaults.DEFAULT_NAME;
                while (repos.containsKey(name)) name += " (default)";
                repos.put(name, com.synapmc.repo.RepositoryDefaults.DEFAULT_MANIFEST);
            }
            defaultReposInitialized = true;
        }
    }

    private static Path path(File roamingDir) {
        return new File(roamingDir, "config/synapmc.json").toPath();
    }
}
