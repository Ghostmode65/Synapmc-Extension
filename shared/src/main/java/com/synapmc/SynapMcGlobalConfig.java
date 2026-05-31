package com.synapmc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

public class SynapMcGlobalConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public Map<String, String> unifyFolders = new LinkedHashMap<String, String>();
    public Map<String, String> pathTags = new LinkedHashMap<String, String>();

    public static SynapMcGlobalConfig load(File roamingDir) {
        SynapMcGlobalConfig config = null;
        File file = file(roamingDir);
        if (file.exists()) {
            try {
                String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                config = GSON.fromJson(json, SynapMcGlobalConfig.class);
            } catch (Exception e) {
                SynapMcShared.error("[SynapMc] Could not load global config: " + e.getMessage());
            }
        }
        if (config == null) config = new SynapMcGlobalConfig();
        config.applyDefaults();
        config.save(roamingDir);
        return config;
    }

    public void save(File roamingDir) {
        try {
            File file = file(roamingDir);
            file.getParentFile().mkdirs();
            Files.write(file.toPath(), GSON.toJson(this).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            SynapMcShared.error("[SynapMc] Could not save global config: " + e.getMessage());
        }
    }

    public void applyDefaults() {
        if (unifyFolders == null) unifyFolders = new LinkedHashMap<String, String>();
        if (pathTags == null) pathTags = new LinkedHashMap<String, String>();
        putDefault(unifyFolders, "macros", "scripts/macros");
        putDefault(pathTags, "library", "scripts/library");
        putDefault(pathTags, "macros", "scripts/macros");
        putDefault(pathTags, "projects", "projects");
        putDefault(pathTags, "images", "assets/images");
        putDefault(pathTags, "sounds", "assets/sounds");
    }

    private static void putDefault(Map<String, String> map, String key, String value) {
        if (!map.containsKey(key)) map.put(key, value);
    }

    private static File file(File roamingDir) {
        return new File(roamingDir, "synapmc/global.json");
    }
}
