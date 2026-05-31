package com.synapmc.repo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.synapmc.SynapMcShared;
import com.synapmc.model.RepoManifest;
import com.synapmc.model.ScriptEntry;
import com.synapmc.model.ScriptRegister;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class RepoManager {


    public static class InstalledInfo {
        public String path;
        public String version;
        public String repoName;
    }

    private static RepoManager INSTANCE;

    public static synchronized RepoManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new RepoManager();
        }
        return INSTANCE;
    }


    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private File roamingDir;
    private final Map<String, RepoManifest> manifests = new LinkedHashMap<String, RepoManifest>();
    private final Map<String, InstalledInfo> installed = new LinkedHashMap<String, InstalledInfo>();
    private final Set<String> favorites = new java.util.LinkedHashSet<String>();
    private String lastFetchedName;
    private final DashboardServer dashboard = new DashboardServer();

    private RepoManager() {}

    public DashboardServer getDashboard() { return dashboard; }

    public void init(File roamingDir) {
        this.roamingDir = roamingDir;
        new File(roamingDir, "repos").mkdirs();
        loadInstalledIndex();
        loadFavorites();
        for (String url : SynapMcShared.core.getConfiguredRepos().values()) {
            try {
                fetchAndCacheManifest(url);
            } catch (Exception e) {
                SynapMcShared.error("[SynapMc] Failed to fetch manifest from " + url + ": " + e.getMessage());
            }
        }
        applyRegisters();
    }

    public void addRepo(String url) {
        addRepo(url, true);
    }

    /**
     * @param overwrite
     */
    public void addRepo(String url, boolean overwrite) {
        try {
            fetchAndCacheManifest(url);
        } catch (Exception e) {
            throw new RuntimeException("[SynapMc] Failed to add repo from " + url + ": " + e.getMessage(), e);
        }
        RepoManifest manifest = manifests.get(lastFetchedName);
        if (manifest != null) {
            boolean alreadyPresent = SynapMcShared.core.getConfiguredRepos().containsKey(manifest.name);
            if (!alreadyPresent || overwrite) {
                SynapMcShared.core.addConfigRepo(manifest.name, url);
            }
        }
    }

    public void removeRepo(String name) {
        manifests.remove(name.toLowerCase());
        SynapMcShared.core.removeConfigRepo(name);
    }

    public boolean isInstalled(String name) {
        return installed.containsKey(name.toLowerCase());
    }

    public void addRepos(List<String> urls) {
        for (String url : urls) {
            try {
                addRepo(url);
            } catch (Exception e) {
                SynapMcShared.error("[SynapMc] bulk-add failed for " + url + ": " + e.getMessage());
            }
        }
    }

    public void refreshRepos() {
        manifests.clear();
        for (String url : SynapMcShared.core.getConfiguredRepos().values()) {
            try {
                fetchAndCacheManifest(url);
            } catch (Exception e) {
                SynapMcShared.error("[SynapMc] refresh: failed to fetch manifest from " + url + ": " + e.getMessage());
            }
        }
        SynapMcShared.info("[SynapMc] Repos refreshed (" + manifests.size() + " loaded).");
    }

    public String installScript(String name, String location) {
        return installScript(name, location, new HashSet<String>());
    }

    private String installScript(String name, String location, Set<String> visited) {
        if (!visited.add(name.toLowerCase())) {
            SynapMcShared.error("[SynapMc] install: dependency cycle detected at: " + name);
            return null;
        }

        ScriptEntry entry = findEntry(name);
        if (entry == null) {
            SynapMcShared.error("[SynapMc] install: script not found in any manifest: " + name);
            return null;
        }

        if (entry.dependencies != null) {
            for (String dep : entry.dependencies) {
                if (!isInstalled(dep)) {
                    SynapMcShared.info("[SynapMc] install: installing dependency '" + dep + "' for '" + name + "'");
                    installScript(dep, location, new HashSet<String>(visited));
                }
            }
        }

        InstalledInfo existing = installed.get(name.toLowerCase());
        if (existing != null && existing.version != null && entry.version != null) {
            if (compareVersions(entry.version, existing.version) <= 0) {
                SynapMcShared.info("[SynapMc] install: " + name + " is up-to-date (" + existing.version + "), skipping.");
                return existing.path;
            }
        }

        String destRel = resolveDestination(entry, location);
        File destDir = new File(roamingDir, destRel);
        destDir.mkdirs();

        String filename = entry.source.substring(entry.source.lastIndexOf('/') + 1);
        File destFile = new File(destDir, filename);

        try {
            byte[] bytes = SynapMcShared.core.httpGet(entry.source);
            if (bytes == null || bytes.length == 0) {
                SynapMcShared.error("[SynapMc] install: empty response from " + entry.source);
                return null;
            }
            Files.write(destFile.toPath(), bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            SynapMcShared.error("[SynapMc] install: download failed for " + name + ": " + e.getMessage());
            return null;
        }

        InstalledInfo info = new InstalledInfo();
        info.path = destFile.getAbsolutePath();
        info.version = entry.version;
        info.repoName = entry.repoName;
        installed.put(name.toLowerCase(), info);
        saveInstalledIndex();

        if (entry.register != null && SynapMcShared.core.isAutoRegisterTriggers()) {
            applyRegister(entry, destFile);
        }

        SynapMcShared.info("[SynapMc] Installed " + name + " -> " + destFile.getAbsolutePath());

        if (SynapMcShared.server != null && SynapMcShared.server.hasClients()) {
            Map<String, String> notif = new LinkedHashMap<String, String>();
            notif.put("type", "installed");
            notif.put("name", name);
            notif.put("path", destFile.getAbsolutePath());
            SynapMcShared.server.broadcast(GSON.toJson(notif));
        }

        return destFile.getAbsolutePath();
    }

    public boolean removeScript(String name) {
        InstalledInfo info = installed.remove(name.toLowerCase());
        if (info == null) {
            SynapMcShared.error("[SynapMc] remove: script not installed: " + name);
            return false;
        }
        File f = new File(info.path);
        if (f.exists() && !f.delete()) {
            SynapMcShared.error("[SynapMc] remove: could not delete file: " + info.path);
        }
        removeTrigger(f.getName());
        saveInstalledIndex();
        pruneEmptyDirs(f);
        return true;
    }

    public List<Object> listAll() {
        List<Object> result = new ArrayList<Object>();
        for (Map.Entry<String, RepoManifest> mEntry : manifests.entrySet()) {
            RepoManifest manifest = mEntry.getValue();
            if (manifest.scripts == null) continue;
            for (ScriptEntry script : manifest.scripts) {
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                row.put("name", script.name);
                row.put("author", script.author);
                row.put("description", script.description);
                row.put("version", script.version);
                row.put("repoName", manifest.name);
                row.put("category", script.category);
                row.put("isExecuteReady", script.isExecuteReady);
                InstalledInfo info = installed.get(script.name.toLowerCase());
                row.put("installed", info != null);
                row.put("installedVersion", info != null ? info.version : null);
                row.put("path", info != null ? info.path : null);
                result.add(row);
            }
        }
        return result;
    }

    public String installScript(String name, String location, String repoName) {
        ScriptEntry entry = findEntry(name, repoName);
        if (entry == null) {
            SynapMcShared.error("[SynapMc] install: script '" + name + "' not found in repo '" + repoName + "'");
            return null;
        }
        return installScript(name, location, new HashSet<String>());
    }

    public String installJar(String name, String repoScope) {
        ScriptEntry entry = repoScope != null ? findEntry(name, repoScope) : findEntry(name);
        if (entry == null) {
            SynapMcShared.error("[SynapMc] installJar: script not found: " + name);
            return null;
        }

      
        String jsmVer = entry.jsmacrosVersion != null ? entry.jsmacrosVersion.toLowerCase() : "";
        File jarDir;
        if (jsmVer.equals("2.0.0") || jsmVer.equals("community")) {
            jarDir = new File(roamingDir.getParentFile(), "Extensions");
        } else {
            try {
                java.security.CodeSource cs = RepoManager.class.getProtectionDomain().getCodeSource();
                jarDir = cs != null ? new File(cs.getLocation().toURI()).getParentFile() : roamingDir;
            } catch (Exception e) {
                SynapMcShared.error("[SynapMc] installJar: could not locate JAR dir, falling back to roaming: " + e.getMessage());
                jarDir = roamingDir;
            }
        }
        jarDir.mkdirs();

        String filename = entry.source.substring(entry.source.lastIndexOf('/') + 1);
        File destFile = new File(jarDir, filename);

        try {
            byte[] bytes = SynapMcShared.core.httpGet(entry.source);
            if (bytes == null || bytes.length == 0) {
                SynapMcShared.error("[SynapMc] installJar: empty response from " + entry.source);
                return null;
            }
            Files.write(destFile.toPath(), bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            SynapMcShared.error("[SynapMc] installJar: download failed for " + name + ": " + e.getMessage());
            return null;
        }

        InstalledInfo info = new InstalledInfo();
        info.path = destFile.getAbsolutePath();
        info.version = entry.version;
        info.repoName = entry.repoName;
        installed.put(name.toLowerCase(), info);
        saveInstalledIndex();

        SynapMcShared.info("[SynapMc] Installed JAR: " + name + " -> " + destFile.getAbsolutePath());
        SynapMcShared.info("[SynapMc] *** Restart JsMacros for the extension to load. ***");
        return destFile.getAbsolutePath();
    }

    public String installExe(String name, String repoScope) {
        ScriptEntry entry = repoScope != null ? findEntry(name, repoScope) : findEntry(name);
        if (entry == null) {
            SynapMcShared.error("[SynapMc] installExe: script not found: " + name);
            return null;
        }

        File desktop;
        try {
            desktop = javax.swing.filechooser.FileSystemView.getFileSystemView().getHomeDirectory();
        } catch (Exception e) {
            desktop = new File(System.getProperty("user.home"), "Desktop");
        }
        desktop.mkdirs();

        String filename = entry.source.substring(entry.source.lastIndexOf('/') + 1);
        File destFile = new File(desktop, filename);

        try {
            byte[] bytes = SynapMcShared.core.httpGet(entry.source);
            if (bytes == null || bytes.length == 0) {
                SynapMcShared.error("[SynapMc] installExe: empty response from " + entry.source);
                return null;
            }
            Files.write(destFile.toPath(), bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            SynapMcShared.error("[SynapMc] installExe: download failed for " + name + ": " + e.getMessage());
            return null;
        }

        SynapMcShared.info("[SynapMc] Downloaded EXE to Desktop: " + destFile.getAbsolutePath());
        return destFile.getAbsolutePath();
    }

    public List<Object> listByRepo(String repoName) {
        List<Object> result = new ArrayList<Object>();
        for (Map.Entry<String, RepoManifest> mEntry : manifests.entrySet()) {
            RepoManifest manifest = mEntry.getValue();
            if (!repoName.equalsIgnoreCase(manifest.name)) continue;
            if (manifest.scripts == null) continue;
            for (ScriptEntry script : manifest.scripts) {
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                row.put("name", script.name);
                row.put("author", script.author);
                row.put("description", script.description);
                row.put("version", script.version);
                row.put("repoName", manifest.name);
                row.put("category", script.category);
                row.put("isExecuteReady", script.isExecuteReady);
                InstalledInfo info = installed.get(script.name.toLowerCase());
                row.put("installed", info != null);
                row.put("installedVersion", info != null ? info.version : null);
                row.put("path", info != null ? info.path : null);
                result.add(row);
            }
        }
        return result;
    }

    public String getString(String name) {
        return getString(name, false);
    }

    /**
     * @param source if true, always fetches from the manifest source URL,
     */
    public String getString(String name, boolean source) {
        if (!source) {
            InstalledInfo info = installed.get(name.toLowerCase());
            if (info != null) {
                File f = new File(info.path);
                if (f.exists()) {
                    try {
                        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        SynapMcShared.error("[SynapMc] getString: read failed for " + info.path + ": " + e.getMessage());
                    }
                }
            }
        }

        ScriptEntry entry = findEntry(name);
        if (entry == null || entry.source == null) {
            SynapMcShared.error("[SynapMc] getString: script not found: " + name);
            return null;
        }

        try {
            return SynapMcShared.core.httpGetText(entry.source);
        } catch (Exception e) {
            SynapMcShared.error("[SynapMc] getString: fetch failed for " + name + ": " + e.getMessage());
            return null;
        }
    }

    public void applyRegisters() {
        if (!SynapMcShared.core.isAutoRegisterTriggers()) return;
        for (Map.Entry<String, InstalledInfo> entry : installed.entrySet()) {
            String scriptName = entry.getKey();
            InstalledInfo info = entry.getValue();
            ScriptEntry scriptEntry = findEntry(scriptName);
            if (scriptEntry == null || scriptEntry.register == null) continue;
            File scriptFile = new File(info.path);
            if (!scriptFile.exists()) continue;
            applyRegister(scriptEntry, scriptFile);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void applyRegister(ScriptEntry entry, File scriptFile) {
        ScriptRegister reg = entry.register;
        if (reg == null) return;

        if ("service".equalsIgnoreCase(reg.type)) {
            String lang = null;
            String fname = scriptFile.getName();
            if (fname.endsWith(".lua")) lang = "lua";
            else if (fname.endsWith(".js")) lang = "js";
            else if (fname.endsWith(".py")) lang = "py";
            if (lang == null) {
                SynapMcShared.error("[SynapMc] applyRegister: unknown language for service: " + fname);
                return;
            }
            try {
                if (!SynapMcShared.core.hasExtensionForFile(scriptFile)) {
                    SynapMcShared.error("[SynapMc] No language extension found for " + fname);
                    return;
                }
                String content = new String(Files.readAllBytes(scriptFile.toPath()), StandardCharsets.UTF_8);
                final String finalLang = lang;
                SynapMcShared.core.exec(finalLang, content, scriptFile,
                    ex -> SynapMcShared.error("[SynapMc] service error in " + fname + ": " + ex.getMessage()));
                SynapMcShared.info("[SynapMc] Started service: " + entry.name);
            } catch (Exception e) {
                SynapMcShared.error("[SynapMc] applyRegister: failed to start service " + entry.name + ": " + e.getMessage());
            }
            return;
        }

        try {
            String triggerClassName = SynapMcShared.core.getScriptTriggerClassName();
            Class<?> triggerClass = Class.forName(triggerClassName);
            Class<?> triggerTypeClass = Class.forName(triggerClassName + "$TriggerType");

            Object triggerTypeValue;
            if ("keydown".equalsIgnoreCase(reg.type)) {
                triggerTypeValue = Enum.valueOf((Class<Enum>) triggerTypeClass, "KEY_RISING");
            } else if ("keyup".equalsIgnoreCase(reg.type)) {
                triggerTypeValue = Enum.valueOf((Class<Enum>) triggerTypeClass, "KEY_FALLING");
            } else if ("event".equalsIgnoreCase(reg.type)) {
                triggerTypeValue = Enum.valueOf((Class<Enum>) triggerTypeClass, "EVENT");
            } else {
                SynapMcShared.error("[SynapMc] applyRegister: unknown trigger type: " + reg.type);
                return;
            }

            String eventOrKey;
            if ("event".equalsIgnoreCase(reg.type)) {
                eventOrKey = reg.event != null ? reg.event : "";
            } else {
                eventOrKey = reg.key != null ? reg.key : "";
            }

            String repoSubDir = sanitizeFilename(entry.repoName != null ? entry.repoName : "unknown");
            String filePath = "unified/" + repoSubDir + "/" + scriptFile.getName();

            Object profile = SynapMcShared.core.getProfile();
            Object registry = profile.getClass().getMethod("getRegistry").invoke(profile);
            Method getTriggersMethod = registry.getClass().getMethod("getScriptTriggers");
            List<?> triggers = (List<?>) getTriggersMethod.invoke(registry);

            for (Object t : triggers) {
                Object sf = t.getClass().getField("scriptFile").get(t);
                if (sf != null && sf.toString().equals(filePath)) {
                    return;
                }
            }

            Constructor<?> ctor = triggerClass.getConstructor(
                    triggerTypeClass, String.class, String.class, boolean.class, boolean.class);
            Object trigger = ctor.newInstance(triggerTypeValue, eventOrKey, filePath, true, false);

            Method addMethod = registry.getClass().getMethod("addScriptTrigger", triggerClass);
            addMethod.invoke(registry, trigger);

            SynapMcShared.info("[SynapMc] Registered trigger for " + entry.name + " (" + reg.type + ")");

        } catch (ClassNotFoundException e) {
            SynapMcShared.error("[SynapMc] applyRegister: ScriptTrigger class not found: " + e.getMessage());
        } catch (Exception e) {
            SynapMcShared.error("[SynapMc] applyRegister: failed for " + entry.name + ": " + e);
        }
    }

    private void fetchAndCacheManifest(String url) throws Exception {
        fetchAndCacheManifest(url, new HashSet<String>());
    }

    private void fetchAndCacheManifest(String url, Set<String> inFlight) throws Exception {
        String normalizedUrl = url.split("\\?")[0];
        if (!inFlight.add(normalizedUrl)) {
            SynapMcShared.error("[SynapMc] Manifest cycle detected, skipping: " + url);
            return;
        }
        fetchAndCacheManifestInternal(url, inFlight);
    }

    private void fetchAndCacheManifestInternal(String url, Set<String> inFlight) throws Exception {
        String fetchUrl = url + (url.contains("?") ? "&" : "?") + "_t=" + System.currentTimeMillis();
        String json = SynapMcShared.core.httpGetText(fetchUrl);
        if (json == null || json.isEmpty()) {
            RepoManifest generated = tryGenerateGithubManifest(url);
            if (generated != null) {
                cacheManifest(generated, url);
                return;
            }
            throw new IOException("Empty response from " + url);
        }

        RepoManifest manifest;
        try {
            manifest = GSON.fromJson(json, RepoManifest.class);
        } catch (JsonSyntaxException e) {
            RepoManifest generated = tryGenerateGithubManifest(url);
            if (generated != null) {
                cacheManifest(generated, url);
                return;
            }
            throw new IOException("Invalid JSON from " + url + ": " + e.getMessage());
        }

        if (manifest.name == null || manifest.name.isEmpty()) {
            RepoManifest generated = tryGenerateGithubManifest(url);
            if (generated != null) {
                cacheManifest(generated, url);
                return;
            }
            throw new IOException("Manifest from " + url + " has no 'name' field");
        }

        manifest.manifestUrl = url;

        if (manifest.scripts != null) {
            for (ScriptEntry entry : manifest.scripts) {
                entry.repoName = manifest.name;
            }
        }

        cacheManifest(manifest, url);

        if (manifest.repos != null) {
            for (String subUrl : manifest.repos) {
                if (subUrl == null || subUrl.isEmpty()) continue;
                try {
                    fetchAndCacheManifest(subUrl, inFlight);
                } catch (Exception e) {
                    SynapMcShared.error("[SynapMc] Failed to fetch sub-repo " + subUrl + ": " + e.getMessage());
                }
            }
        }
    }

    private void cacheManifest(RepoManifest manifest, String url) throws IOException {
        manifest.manifestUrl = url;
        if (manifest.scripts != null) {
            for (ScriptEntry entry : manifest.scripts) {
                entry.repoName = manifest.name;
            }
        }
        File cacheFile = new File(roamingDir, "repos/" + sanitizeFilename(manifest.name) + ".json");
        Files.write(cacheFile.toPath(),
                GSON.toJson(manifest).getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        manifests.put(manifest.name.toLowerCase(), manifest);
        lastFetchedName = manifest.name.toLowerCase();
    }

    private RepoManifest tryGenerateGithubManifest(String url) {
        try {
            GithubSource source = parseGithubSource(url);
            if (source == null) return null;
            if (source.branch == null || source.branch.isEmpty()) {
                source.branch = fetchDefaultBranch(source.owner, source.repo);
            }
            if (source.branch == null || source.branch.isEmpty()) source.branch = "main";

            String api = "https://api.github.com/repos/" + source.owner + "/" + source.repo
                    + "/git/trees/" + source.branch + "?recursive=1";
            JsonObject treeRoot = new JsonParser().parse(SynapMcShared.core.httpGetText(api)).getAsJsonObject();
            JsonArray tree = treeRoot.has("tree") ? treeRoot.getAsJsonArray("tree") : null;
            if (tree == null) return null;

            RepoManifest manifest = new RepoManifest();
            manifest.name = source.repo;
            manifest.source = url;
            manifest.author = source.owner;
            manifest.imageUrl = "https://github.com/" + source.owner + ".png";
            manifest.description = "Auto-generated from GitHub source.";

            String prefix = source.path != null && !source.path.isEmpty()
                    ? trimSlashes(source.path) + "/" : "";
            for (JsonElement element : tree) {
                JsonObject node = element.getAsJsonObject();
                if (!"blob".equals(stringField(node, "type"))) continue;
                String path = stringField(node, "path");
                if (path == null || !matchesGithubSourcePath(source, prefix, path) || !isScriptPath(path)) continue;
                ScriptEntry entry = new ScriptEntry();
                entry.name = scriptName(path);
                entry.author = source.owner;
                entry.source = "https://raw.githubusercontent.com/" + source.owner + "/" + source.repo
                        + "/" + source.branch + "/" + path;
                entry.destination = "scripts/macros/";
                entry.version = "1.0";
                entry.description = "Auto-generated macro from " + path;
                entry.category = "macro";
                entry.isExecuteReady = true;
                manifest.scripts.add(entry);
            }
            if (manifest.scripts.isEmpty()) return null;
            SynapMcShared.info("[SynapMc] Auto-generated manifest for GitHub repo " + source.owner + "/" + source.repo
                    + " (" + manifest.scripts.size() + " scripts).");
            return manifest;
        } catch (Exception e) {
            return null;
        }
    }

    private String fetchDefaultBranch(String owner, String repo) {
        try {
            String api = "https://api.github.com/repos/" + owner + "/" + repo;
            JsonObject root = new JsonParser().parse(SynapMcShared.core.httpGetText(api)).getAsJsonObject();
            return stringField(root, "default_branch");
        } catch (Exception e) {
            return null;
        }
    }

    private GithubSource parseGithubSource(String url) {
        try {
            String clean = url.replace('\\', '/').replaceAll("[?#].*", "");
            GithubSource source = new GithubSource();
            if (clean.contains("raw.githubusercontent.com/")) {
                String[] parts = clean.substring(clean.indexOf("raw.githubusercontent.com/") + 26).split("/");
                if (parts.length < 4) return null;
                source.owner = parts[0];
                source.repo = parts[1];
                source.branch = parts[2];
                setSourcePath(source, join(parts, 3));
                return source;
            }
            if (!clean.contains("github.com/")) return null;
            String[] parts = clean.substring(clean.indexOf("github.com/") + 11).split("/");
            if (parts.length < 2) return null;
            source.owner = parts[0];
            source.repo = parts[1].replaceAll("\\.git$", "");
            if (parts.length >= 4 && "tree".equals(parts[2])) {
                source.branch = parts[3];
                source.path = parts.length > 4 ? join(parts, 4) : "";
            } else if (parts.length >= 4 && "blob".equals(parts[2])) {
                source.branch = parts[3];
                setSourcePath(source, parts.length > 4 ? join(parts, 4) : "");
            } else {
                source.path = "";
            }
            return source;
        } catch (Exception e) {
            return null;
        }
    }

    private String stringField(JsonObject obj, String field) {
        return obj != null && obj.has(field) && !obj.get(field).isJsonNull()
                ? obj.get(field).getAsString() : null;
    }

    private boolean isScriptPath(String path) {
        String lower = path.toLowerCase();
        return lower.endsWith(".lua") || lower.endsWith(".js") || lower.endsWith(".py");
    }

    private boolean matchesGithubSourcePath(GithubSource source, String prefix, String path) {
        if (source.singleScriptPath != null && !source.singleScriptPath.isEmpty()) {
            return path.equals(source.singleScriptPath);
        }
        return path.startsWith(prefix);
    }

    private void setSourcePath(GithubSource source, String rawPath) {
        String path = trimSlashes(rawPath != null ? rawPath : "");
        if (isScriptPath(path)) {
            source.singleScriptPath = path;
            source.path = parentPath(path);
        } else if (looksLikeFile(path)) {
            source.path = parentPath(path);
        } else {
            source.path = path;
        }
    }

    private boolean looksLikeFile(String path) {
        if (path == null || path.isEmpty()) return false;
        String name = path.substring(path.lastIndexOf('/') + 1);
        return name.contains(".");
    }

    private String parentPath(String path) {
        int slash = path.lastIndexOf('/');
        return slash > 0 ? path.substring(0, slash) : "";
    }

    private String scriptName(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private String trimSlashes(String value) {
        return value.replaceAll("^/+", "").replaceAll("/+$", "");
    }

    private String join(String[] parts, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < parts.length; i++) {
            if (i > start) sb.append('/');
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    private static class GithubSource {
        String owner;
        String repo;
        String branch;
        String path;
        String singleScriptPath;
    }

    private ScriptEntry findEntry(String name) {
        return findEntry(name, null);
    }

    private ScriptEntry findEntry(String name, String repoName) {
        for (RepoManifest manifest : manifests.values()) {
            if (repoName != null && !repoName.equalsIgnoreCase(manifest.name)) continue;
            if (manifest.scripts == null) continue;
            for (ScriptEntry entry : manifest.scripts) {
                if (entry.name != null && entry.name.equalsIgnoreCase(name)) {
                    return entry;
                }
            }
        }
        return null;
    }

    private String resolveDestination(ScriptEntry entry, String location) {
        if (entry.destination != null && !entry.destination.isEmpty()) {
            String dest = entry.destination.endsWith("/") ? entry.destination : entry.destination + "/";
            return dest + sanitizeFilename(entry.repoName != null ? entry.repoName : "unknown") + "/";
        }

        String repoSub = sanitizeFilename(entry.repoName != null ? entry.repoName : "unknown");
        if ("load".equalsIgnoreCase(location)) {
            return "scripts/load/" + repoSub + "/";
        }
        if (location == null || "library".equalsIgnoreCase(location)) {
            if (entry.register != null) {
                return "scripts/macros/" + repoSub + "/";
            }
            return "scripts/library/" + repoSub + "/";
        }
        return "scripts/" + (location.endsWith("/") ? location : location + "/");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void removeTrigger(String filename) {
        try {
            String triggerClassName = SynapMcShared.core.getScriptTriggerClassName();
            Class<?> triggerClass = Class.forName(triggerClassName);

            Object profile = SynapMcShared.core.getProfile();
            Object registry = profile.getClass().getMethod("getRegistry").invoke(profile);
            Method getTriggersMethod = registry.getClass().getMethod("getScriptTriggers");
            List<?> triggers = (List<?>) getTriggersMethod.invoke(registry);

            List<Object> toRemove = new ArrayList<Object>();
            for (Object t : triggers) {
                Object sf = t.getClass().getField("scriptFile").get(t);
                if (sf != null && sf.toString().equals(filename)) {
                    toRemove.add(t);
                }
            }

            Method removeMethod = registry.getClass().getMethod("removeScriptTrigger", triggerClass);
            for (Object t : toRemove) {
                removeMethod.invoke(registry, t);
            }
        } catch (Exception e) {
            SynapMcShared.error("[SynapMc] removeTrigger: " + e.getMessage());
        }
    }

    private void loadInstalledIndex() {
        File indexFile = new File(roamingDir, "repos/installed.json");
        if (!indexFile.exists()) return;
        try {
            String json = new String(Files.readAllBytes(indexFile.toPath()), StandardCharsets.UTF_8);
            Type type = new TypeToken<Map<String, InstalledInfo>>() {}.getType();
            Map<String, InstalledInfo> loaded = GSON.fromJson(json, type);
            if (loaded != null) installed.putAll(loaded);
        } catch (Exception e) {
            SynapMcShared.error("[SynapMc] Could not load installed.json: " + e.getMessage());
        }
    }

    private void saveInstalledIndex() {
        File indexFile = new File(roamingDir, "repos/installed.json");
        try {
            Files.write(indexFile.toPath(),
                    GSON.toJson(installed).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            SynapMcShared.error("[SynapMc] Could not save installed.json: " + e.getMessage());
        }
    }

    private int compareVersions(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        String[] partsA = a.split("\\.");
        String[] partsB = b.split("\\.");
        int len = Math.max(partsA.length, partsB.length);
        for (int i = 0; i < len; i++) {
            String segA = i < partsA.length ? partsA[i] : "0";
            String segB = i < partsB.length ? partsB[i] : "0";
            int cmp;
            try {
                cmp = Integer.compare(Integer.parseInt(segA), Integer.parseInt(segB));
            } catch (NumberFormatException e) {
                cmp = segA.compareTo(segB);
            }
            if (cmp != 0) return cmp;
        }
        return 0;
    }

    private String sanitizeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    public ScriptEntry findEntryPublic(String name) {
        return findEntry(name);
    }

    Map<String, RepoManifest> getManifests() { return manifests; }
    Map<String, InstalledInfo> getInstalled() { return installed; }

    public Map<String, String> getRepos() {
        return SynapMcShared.core.getConfiguredRepos();
    }

    public Set<String> getFavorites() {
        return java.util.Collections.unmodifiableSet(favorites);
    }

    public boolean isFavorite(String name) {
        return favorites.contains(name.toLowerCase());
    }

    public void addFavorite(String name) {
        favorites.add(name.toLowerCase());
        saveFavorites();
    }

    public void removeFavorite(String name) {
        favorites.remove(name.toLowerCase());
        saveFavorites();
    }

    private void loadFavorites() {
        File f = new File(roamingDir, "repos/favorites.json");
        if (!f.exists()) return;
        try {
            String json = new String(java.nio.file.Files.readAllBytes(f.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            List<String> list = GSON.fromJson(json,
                    com.google.gson.reflect.TypeToken.getParameterized(List.class, String.class).getType());
            if (list != null) {
                favorites.clear();
                for (String s : list) favorites.add(s.toLowerCase());
            }
        } catch (Exception e) {
            SynapMcShared.error("[SynapMc] Failed to load favorites: " + e.getMessage());
        }
    }

    private void saveFavorites() {
        File f = new File(roamingDir, "repos/favorites.json");
        try {
            java.nio.file.Files.write(f.toPath(),
                    GSON.toJson(new ArrayList<String>(favorites)).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            SynapMcShared.error("[SynapMc] Failed to save favorites: " + e.getMessage());
        }
    }

    private static final java.util.Set<String> PROTECTED_REL_PATHS;
    static {
        java.util.Set<String> s = new java.util.HashSet<String>();
        s.add("scripts");
        s.add("scripts/macros");
        s.add("scripts/library");
        s.add("scripts/load");
        s.add("scripts/plugins");
        PROTECTED_REL_PATHS = java.util.Collections.unmodifiableSet(s);
    }

    private void pruneEmptyDirs(File deletedFile) {
        if (roamingDir == null) return;
        // Build the set of protected absolute paths: static defaults + configured unify folders
        java.util.Set<String> protectedPaths = new java.util.HashSet<String>();
        for (String rel : PROTECTED_REL_PATHS) {
            protectedPaths.add(new File(roamingDir, rel).getAbsolutePath());
        }
        protectedPaths.add(roamingDir.getAbsolutePath());
        Map<String, String> unifyFolders = SynapMcShared.core != null
                ? SynapMcShared.core.getUnifyFolders() : null;
        if (unifyFolders != null) {
            for (String rel : unifyFolders.values()) {
                protectedPaths.add(new File(roamingDir, rel).getAbsolutePath());
            }
        }

        File dir = deletedFile.getParentFile();
        while (dir != null) {
            String abs = dir.getAbsolutePath();
            if (protectedPaths.contains(abs)) break;
            // Stop if outside roamingDir
            if (!abs.startsWith(roamingDir.getAbsolutePath())) break;
            String[] contents = dir.list();
            if (contents != null && contents.length == 0) {
                if (!dir.delete()) break;
                dir = dir.getParentFile();
            } else {
                break;
            }
        }
    }
}

