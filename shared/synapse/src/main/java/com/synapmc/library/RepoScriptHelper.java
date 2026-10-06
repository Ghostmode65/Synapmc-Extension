package com.synapmc.library;

import com.synapmc.SynapMcShared;
import com.synapmc.model.ScriptEntry;
import com.synapmc.repo.RepoManager;
import java.io.File;
import java.nio.file.Files;
import java.util.List;

public final class RepoScriptHelper {
    private final String repo;
    private final RepoManager manager = RepoManager.getInstance();
    public RepoScriptHelper(String repo) { this.repo = repo; }
    public List<String> list() { return manager.scriptNames(repo, null); }
    public List<String> list(boolean installed) { return manager.scriptNames(repo, installed); }
    public boolean isInstalled(String name) { return getLocation(name) != null; }
    public String getLocation(String name) {
        RepoManager.InstalledInfo info = manager.getInstalledInfo(name);
        return info != null && (repo == null || repo.equalsIgnoreCase(info.repoName)) ? info.path : null;
    }
    public String getString(String name) { return getString(name, false); }
    public String getString(String name, boolean fromSource) {
        try {
            String path = getLocation(name);
            if (!fromSource && path != null && Files.isRegularFile(new File(path).toPath()))
                return Files.readString(new File(path).toPath());
            ScriptEntry entry = manager.findEntry(name, repo);
            return entry == null ? null : SynapMcShared.core.httpGetText(entry.source);
        } catch (Exception error) {
            SynapMcShared.error("[SynapMc] Could not read " + name + ": " + error);
            return null;
        }
    }
    public String install(String name) {
        ScriptEntry entry = manager.findEntry(name, repo);
        if (entry == null) return null;
        if ("extension".equalsIgnoreCase(entry.category) || "mod".equalsIgnoreCase(entry.category)
                || "jar".equalsIgnoreCase(entry.category)) return manager.installJar(name, repo);
        if ("exe".equalsIgnoreCase(entry.category) || "appimage".equalsIgnoreCase(entry.category))
            return manager.installExe(name, repo);
        return manager.installScript(name, null, repo);
    }
    public boolean remove(String name) { return isInstalled(name) && manager.removeScript(name); }
    public boolean execute(String name) {
        String path = getLocation(name);
        if (path == null) return false;
        try {
            File file = new File(path);
            String language = SynapMcShared.core.getLanguageForFile(file);
            if (language == null) return false;
            SynapMcShared.core.exec(language, Files.readString(file.toPath()), file,
                    error -> SynapMcShared.error("[SynapMc] Script failed: " + name + ": " + error));
            return true;
        } catch (Exception error) {
            SynapMcShared.error("[SynapMc] Could not execute " + name + ": " + error);
            return false;
        }
    }
}
