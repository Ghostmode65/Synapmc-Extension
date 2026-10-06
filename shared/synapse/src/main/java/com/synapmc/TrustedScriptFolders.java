package com.synapmc;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class TrustedScriptFolders {
    private static final Gson GSON = new Gson();
    private static final Set<Path> trusted = new LinkedHashSet<Path>();
    private static Path loadedFrom;

    private TrustedScriptFolders() {}

    public static synchronized Path firstUntrusted(List<ScriptDropService.Entry> entries) throws IOException {
        load();
        for (ScriptDropService.Entry entry : entries) {
            if (entry.problem != null) continue;
            Path folder = entry.source.toPath().toRealPath().getParent();
            if (!isTrusted(folder)) return folder;
        }
        return null;
    }

    public static synchronized void trust(Path folder) throws IOException {
        load();
        Path real = folder.toRealPath();
        if (!trusted.add(real)) return;
        try { save(); }
        catch (IOException error) { trusted.remove(real); throw error; }
    }

    private static boolean isTrusted(Path folder) throws IOException {
        Path scripts = canonical(SynapMcShared.roamingDir.toPath().resolve("scripts"));
        Path configMacros = canonical(SynapMcShared.core.getMinecraftDir().toPath().resolve("config/macros"));
        Path configuredMacros = canonical(SynapMcShared.core.getMacroFolder().toPath());
        if (folder.startsWith(scripts) || folder.startsWith(configMacros) || folder.startsWith(configuredMacros)) return true;
        for (Path root : trusted) if (folder.startsWith(root)) return true;
        return false;
    }

    private static Path canonical(Path path) throws IOException {
        return Files.exists(path) ? path.toRealPath() : path.toAbsolutePath().normalize();
    }

    private static void load() throws IOException {
        Path path = storePath();
        if (path.equals(loadedFrom)) return;
        trusted.clear();
        if (Files.isRegularFile(path)) {
            try {
                JsonArray values = JsonParser.parseString(new String(Files.readAllBytes(path), StandardCharsets.UTF_8)).getAsJsonArray();
                for (JsonElement value : values) {
                    if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                        Path folder = Path.of(value.getAsString());
                        if (folder.isAbsolute()) trusted.add(canonical(folder));
                    }
                }
            } catch (Exception error) {
                trusted.clear();
                SynapMcShared.error("[SynapMc] Could not read trusted folders; custom folders will need approval again: " + error);
            }
        }
        loadedFrom = path;
    }

    private static void save() throws IOException {
        Path path = storePath();
        Files.createDirectories(path.getParent());
        JsonArray values = new JsonArray();
        for (Path folder : trusted) values.add(folder.toString());
        Path temporary = Files.createTempFile(path.getParent(), "trusted-folders-", ".tmp");
        try {
            Files.write(temporary, GSON.toJson(values).getBytes(StandardCharsets.UTF_8));
            try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Path storePath() {
        return SynapMcShared.roamingDir.toPath().resolve("config/trusted-folders.json").toAbsolutePath().normalize();
    }
}
