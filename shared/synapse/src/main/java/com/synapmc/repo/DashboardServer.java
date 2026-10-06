package com.synapmc.repo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.synapmc.SynapMcShared;
import com.synapmc.model.RepoManifest;
import com.synapmc.model.ScriptEntry;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class DashboardServer {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private HttpServer httpServer;
    private int port;
    private byte[] dashboardHtml;

    public void openDashboard() {
        if (httpServer == null) {
            startServer();
        }
        openBrowser("http://127.0.0.1:" + port + "/");
    }

    private void startServer() {
        try {
            if (dashboardHtml == null) {
                try (InputStream input = DashboardServer.class.getResourceAsStream("dashboard.html")) {
                    if (input == null) throw new IOException("Missing dashboard.html resource");
                    dashboardHtml = input.readAllBytes();
                }
            }
            InetSocketAddress addr = new InetSocketAddress("127.0.0.1", 0);
            httpServer = HttpServer.create(addr, 0);
            port = httpServer.getAddress().getPort();

            createContext("/", "GET", this::root);
            createContext("/api/scripts", "GET", this::scriptsApi);
            createContext("/api/scripts/install", "POST", this::scriptsInstall);
            createContext("/api/scripts/remove", "POST", this::scriptsRemove);
            createContext("/api/repos", "GET", this::reposApi);
            createContext("/api/repos/add", "POST", this::reposAdd);
            createContext("/api/repos/bulk-add", "POST", this::reposBulkAdd);
            createContext("/api/repos/remove", "POST", this::reposRemove);
            createContext("/api/repos/hide", "POST", exchange -> setRepoHidden(exchange, true));
            createContext("/api/repos/show", "POST", exchange -> setRepoHidden(exchange, false));
            createContext("/api/repos/refresh", "POST", this::reposRefresh);
            createContext("/api/status", "GET", this::statusApi);
            createContext("/api/tcpport/start", "POST", this::tcpPortStart);
            createContext("/api/manifest/fetch", "GET", this::manifestFetch);
            createContext("/api/scripts/reveal", "POST", this::reveal);
            createContext("/api/exec", "POST", this::execApi);
            createContext("/api/popular", "GET", this::popular);
            createContext("/api/favorites", "GET", this::favoritesApi);
            createContext("/api/favorites/add", "POST", exchange -> setFavorite(exchange, true));
            createContext("/api/favorites/remove", "POST", exchange -> setFavorite(exchange, false));
            createContext("/api/settings/install-dirs", null, this::installDirs);
            createContext("/api/settings/choose-folder", "POST", this::chooseFolder);
            httpServer.setExecutor(Executors.newCachedThreadPool());
            httpServer.start();

            SynapMcShared.info("[SynapMc] Dashboard available at http://127.0.0.1:" + port + "/");
        } catch (IOException e) {
            SynapMcShared.error("[SynapMc] Failed to start dashboard server: " + e.getMessage());
        }
    }

    private void createContext(String path, String method, HttpHandler handler) {
        HttpContext context = httpServer.createContext(path, handler);
        context.getFilters().add(new Filter() {
            @Override public String description() { return "Restricts the local dashboard to its loopback origin"; }
            @Override public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
                String host = exchange.getRequestHeaders().getFirst("Host");
                String origin = exchange.getRequestHeaders().getFirst("Origin");
                String expected = "127.0.0.1:" + port;
                String local = "localhost:" + port;
                boolean validHost = expected.equalsIgnoreCase(host) || local.equalsIgnoreCase(host);
                boolean validOrigin = origin == null || origin.equalsIgnoreCase("http://" + expected)
                        || origin.equalsIgnoreCase("http://" + local);
                if (!validHost || !validOrigin) {
                    sendResponse(exchange, 403, "text/plain; charset=utf-8", "Forbidden");
                    return;
                }
                if (method != null && !method.equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
                    return;
                }
                chain.doFilter(exchange);
            }
        });
    }

    private void openBrowser(String url) {
        boolean opened = false;
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                opened = true;
            }
        } catch (Exception ignored) {}

        if (!opened) {
            try {
                String os = System.getProperty("os.name", "").toLowerCase();
                ProcessBuilder pb;
                if (os.contains("win")) {
                    pb = new ProcessBuilder("cmd", "/c", "start", "", url);
                } else if (os.contains("mac")) {
                    pb = new ProcessBuilder("open", url);
                } else {
                    pb = new ProcessBuilder("xdg-open", url);
                }
                pb.start();
            } catch (Exception e) {
                SynapMcShared.error("[SynapMc] Could not open browser: " + e.getMessage());
                SynapMcShared.info("[SynapMc] Open manually: " + url);
            }
        }
    }

    private void root(HttpExchange exchange) throws IOException {
        sendResponse(exchange, 200, "text/html; charset=utf-8", dashboardHtml);
    }

    private void scriptsApi(HttpExchange exchange) throws IOException {
        List<Map<String, Object>> scripts = new ArrayList<Map<String, Object>>();
        RepoManager rm = RepoManager.getInstance();
        for (Map.Entry<String, RepoManifest> mEntry : rm.getManifests().entrySet()) {
            RepoManifest manifest = mEntry.getValue();
            if (manifest.scripts == null) continue;
            for (ScriptEntry entry : manifest.scripts) {
                if (!rm.isCompatibleWithCurrentJsMacros(entry)) continue;
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                row.put("name", entry.name);
                row.put("source", entry.source);
                row.put("updatedAt", entry.updated != null ? entry.updated : manifest.fetchedAt);
                row.put("author", entry.author);
                row.put("description", entry.description);
                row.put("changelog", entry.changelog);
                row.put("version", entry.version);
                row.put("imageurl", entry.imageurl);
                row.put("imageWidth", entry.imageWidth);
                row.put("imageHeight", entry.imageHeight);
                row.put("category", "jar".equalsIgnoreCase(entry.category) ? "extension" : entry.category);
                row.put("isExecuteReady", entry.isExecuteReady);
                row.put("jsmacrosVersion", entry.jsmacrosVersion);
                row.put("server", entry.server);
                row.put("repoName", manifest.name);
                row.put("repoSource", manifest.source);
                row.put("repoDiscordUrl", manifest.discordUrl);
                row.put("repoDonateUrl", manifest.donateUrl);
                row.put("repoImageUrl", manifest.imageUrl);
                row.put("repoDescription", manifest.description);
                row.put("repoAutoGenerated", manifest.description != null && manifest.description.startsWith("Auto-generated from GitHub source"));
                row.put("autoGenerated", entry.description != null && entry.description.startsWith("Auto-generated macro from "));
                row.put("mcVersion", entry.mcVersion);
                row.put("extensionEdition", entry.extensionEdition);
                row.put("repoHidden", rm.isRepoHidden(manifest.name));
                row.put("favorited", RepoManager.getInstance().isFavorite(entry.name));
                RepoManager.InstalledInfo info = rm.getInstalledInfo(entry.name);
                row.put("installed", info != null);
                row.put("installedVersion", info != null ? info.version : null);
                row.put("updateAvailable", info != null && entry.version != null && info.version != null && rm.compareVersions(entry.version, info.version) > 0);
                row.put("path", info != null ? info.path : null);
                row.put("hasRegister", entry.register != null);
                String lang = null;
                if (entry.source != null) {
                    int dot = entry.source.lastIndexOf('.');
                    int slash = entry.source.lastIndexOf('/');
                    if (dot > slash && dot >= 0) lang = entry.source.substring(dot + 1).toLowerCase();
                }
                row.put("lang", lang);
                String dest = (entry.destination != null && !entry.destination.isEmpty())
                        ? entry.destination
                        : (entry.register != null ? "scripts/macros/" : "scripts/library/");
                String destNorm = dest.replace("\\", "/").toLowerCase();
                row.put("goesToMacros", destNorm.startsWith("scripts/macros"));
                scripts.add(row);
            }
        }
        sendResponse(exchange, 200, "application/json; charset=utf-8", GSON.toJson(scripts));
    }

    private void reposApi(HttpExchange exchange) throws IOException {
        RepoManager rm = RepoManager.getInstance();
        Map<String, String> repos = rm.getRepos();
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, String> entry : repos.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("url", entry.getValue());
            row.put("hidden", rm.isRepoHidden(entry.getKey()));
            result.put(entry.getKey(), row);
        }
        sendResponse(exchange, 200, "application/json; charset=utf-8", GSON.toJson(result));
    }

    private void reposAdd(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String url;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> req = GSON.fromJson(body, Map.class);
            url = req != null && req.get("url") != null ? req.get("url").toString().trim() : null;
        } catch (Exception e) {
            sendResponse(exchange, 400, "application/json", "{\"error\":\"Invalid JSON\"}");
            return;
        }
        if (url == null || url.isEmpty()) {
            sendResponse(exchange, 400, "application/json", "{\"error\":\"Missing url\"}");
            return;
        }
        try {
            RepoManager.getInstance().addRepo(url);
            sendResponse(exchange, 200, "application/json", "{\"ok\":true}");
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(msg) + "\"}");
        }
    }

    private void reposBulkAdd(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        List<String> urls;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> req = GSON.fromJson(body, Map.class);
            Object raw = req != null ? req.get("urls") : null;
            if (!(raw instanceof List)) {
                sendResponse(exchange, 400, "application/json", "{\"error\":\"Missing urls array\"}");
                return;
            }
            urls = new ArrayList<String>();
            for (Object o : (List<?>) raw) {
                if (o != null && !o.toString().trim().isEmpty()) urls.add(o.toString().trim());
            }
        } catch (Exception e) {
            sendResponse(exchange, 400, "application/json", "{\"error\":\"Invalid JSON\"}");
            return;
        }
        List<String> errors = new ArrayList<String>();
        int added = 0;
        for (String url : urls) {
            try {
                RepoManager.getInstance().addRepo(url);
                added++;
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                errors.add(escapeJson(url) + ": " + escapeJson(msg));
            }
        }
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        resp.put("added", added);
        resp.put("errors", errors);
        sendResponse(exchange, 200, "application/json; charset=utf-8", GSON.toJson(resp));
    }

    private void reposRefresh(HttpExchange exchange) throws IOException {
        RepoManager.getInstance().refreshRepos();
        sendResponse(exchange, 200, "application/json", "{\"ok\":true}");
    }

    private void reposRemove(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> req = GSON.fromJson(body, Map.class);
            String name = req != null && req.get("name") != null ? req.get("name").toString() : null;
            if (name == null || name.isEmpty()) {
                sendResponse(exchange, 400, "application/json", "{\"error\":\"Missing name\"}");
                return;
            }
            RepoManager.getInstance().removeRepo(name);
            sendResponse(exchange, 200, "application/json", "{\"ok\":true}");
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(msg) + "\"}");
        }
    }

    private void setRepoHidden(HttpExchange exchange, boolean hidden) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> req = GSON.fromJson(body, Map.class);
            String name = req != null && req.get("name") != null ? req.get("name").toString() : null;
            if (name == null || name.isEmpty()) {
                sendResponse(exchange, 400, "application/json", "{\"error\":\"Missing name\"}");
                return;
            }
            if (hidden) RepoManager.getInstance().hideRepo(name);
            else RepoManager.getInstance().showRepo(name);
            sendResponse(exchange, 200, "application/json", "{\"ok\":true}");
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(msg) + "\"}");
        }
    }

    private void statusApi(HttpExchange exchange) throws IOException {
        boolean running = SynapMcShared.server != null;
        boolean connected = running && SynapMcShared.server.hasClients();
        sendResponse(exchange, 200, "application/json; charset=utf-8",
                "{\"tcpPortRunning\":" + running + ",\"tcpPortConnected\":" + connected + "}");
    }

    private void tcpPortStart(HttpExchange exchange) throws IOException {
        if (SynapMcShared.server != null) {
            sendResponse(exchange, 200, "application/json", "{\"ok\":true,\"alreadyRunning\":true}");
            return;
        }
        int port = SynapMcShared.core.getTcpPort();
        String password = SynapMcShared.core.getTcpPortPassword();
        SynapMcShared.server = new com.synapmc.server.BridgeServer(port, password,
                SynapMcShared.config.tcpBindAddress);
        try {
            SynapMcShared.server.start();
            sendResponse(exchange, 200, "application/json", "{\"ok\":true,\"alreadyRunning\":false}");
        } catch (IOException e) {
            SynapMcShared.server = null;
            sendResponse(exchange, 500, "application/json",
                    "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private void scriptsInstall(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> req = GSON.fromJson(body, Map.class);
            String name = req != null && req.get("name") != null ? req.get("name").toString() : null;
            String location = req != null && req.get("location") != null ? req.get("location").toString() : "library";
            if (name == null || name.isEmpty()) {
                sendResponse(exchange, 400, "application/json", "{\"error\":\"Missing name\"}");
                return;
            }
            RepoManager rm = RepoManager.getInstance();
            ScriptEntry entry = rm.findEntryPublic(name);
            String category = entry != null ? entry.category : null;
            String path;
            String extra = "";
            if ("jar".equals(category) || "mod".equals(category) || "extension".equals(category)) {
                path = rm.installJar(name, null);
                extra = ",\"restart\":true";
            } else if ("exe".equals(category) || "appimage".equals(category)) {
                path = rm.installExe(name, null);
            } else {
                path = rm.installScript(name, location);
            }
            if (path != null) {
                sendResponse(exchange, 200, "application/json", "{\"ok\":true,\"path\":\"" + escapeJson(path) + "\"" + extra + "}");
            } else {
                sendResponse(exchange, 500, "application/json", "{\"error\":\"Install failed\"}");
            }
        } catch (Exception e) {
            sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private void scriptsRemove(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> req = GSON.fromJson(body, Map.class);
            String name = req != null && req.get("name") != null ? req.get("name").toString() : null;
            if (name == null || name.isEmpty()) {
                sendResponse(exchange, 400, "application/json", "{\"error\":\"Missing name\"}");
                return;
            }
            boolean ok = RepoManager.getInstance().removeScript(name);
            sendResponse(exchange, 200, "application/json", ok ? "{\"ok\":true}" : "{\"error\":\"Script not installed\"}");
        } catch (Exception e) {
            sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private void manifestFetch(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String url = null;
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("url=")) {
                    url = URLDecoder.decode(param.substring(4), StandardCharsets.UTF_8.name());
                    break;
                }
            }
        }
        if (url == null || url.isEmpty()) {
            sendResponse(exchange, 400, "application/json", "{\"error\":\"Missing url parameter\"}");
            return;
        }
        try {
            String json = SynapMcShared.core.httpGetText(url);
            if (json == null || json.isEmpty()) {
                sendResponse(exchange, 502, "application/json", "{\"error\":\"Empty response from URL\"}");
                return;
            }
            sendResponse(exchange, 200, "application/json; charset=utf-8", json);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            sendResponse(exchange, 502, "application/json", "{\"error\":\"" + escapeJson(msg) + "\"}");
        }
    }

    private void reveal(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> req = GSON.fromJson(body, Map.class);
            String name = req != null && req.get("name") != null ? req.get("name").toString() : null;
            if (name == null || name.isEmpty()) {
                sendResponse(exchange, 400, "application/json", "{\"error\":\"Missing name\"}");
                return;
            }
            RepoManager.InstalledInfo info = RepoManager.getInstance().getInstalledInfo(name);
            if (info == null) {
                sendResponse(exchange, 404, "application/json", "{\"error\":\"Script not installed\"}");
                return;
            }
            File folder = new File(info.path).getParentFile();
            if (folder == null || !folder.exists()) {
                sendResponse(exchange, 404, "application/json", "{\"error\":\"Folder not found\"}");
                return;
            }
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(folder);
                } else {
                    String os = System.getProperty("os.name", "").toLowerCase();
                    ProcessBuilder pb;
                    if (os.contains("win")) {
                        pb = new ProcessBuilder("explorer", folder.getAbsolutePath());
                    } else if (os.contains("mac")) {
                        pb = new ProcessBuilder("open", folder.getAbsolutePath());
                    } else {
                        pb = new ProcessBuilder("xdg-open", folder.getAbsolutePath());
                    }
                    pb.start();
                }
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                return;
            }
            sendResponse(exchange, 200, "application/json", "{\"ok\":true}");
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(msg) + "\"}");
        }
    }

    private void execApi(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String scriptName;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> req = GSON.fromJson(body, Map.class);
            scriptName = req != null && req.get("name") != null ? req.get("name").toString() : null;
        } catch (Exception e) {
            sendResponse(exchange, 400, "application/json", "{\"error\":\"Invalid JSON body\"}");
            return;
        }
        if (scriptName == null || scriptName.isEmpty()) {
            sendResponse(exchange, 400, "application/json", "{\"error\":\"Missing name\"}");
            return;
        }
        RepoManager repoManager = RepoManager.getInstance();
        ScriptEntry entry = repoManager.findEntryPublic(scriptName);
        if (entry == null) {
            sendResponse(exchange, 404, "application/json",
                    "{\"error\":\"Script not found: " + escapeJson(scriptName) + "\"}");
            return;
        }
        if (!entry.isExecuteReady) {
            sendResponse(exchange, 400, "application/json",
                    "{\"error\":\"Script is not marked execute-ready: " + escapeJson(scriptName) + "\"}");
            return;
        }
        String content = repoManager.getString(scriptName);
        if (content == null) {
            sendResponse(exchange, 404, "application/json",
                    "{\"error\":\"Script not found: " + escapeJson(scriptName) + "\"}");
            return;
        }
        String lang = detectLang(entry.source);
        if (lang == null) {
            RepoManager.InstalledInfo info = repoManager.getInstalledInfo(scriptName);
            lang = info != null ? detectLang(info.path) : null;
        }
        if (lang == null) {
            sendResponse(exchange, 400, "application/json",
                    "{\"error\":\"Could not detect script language for: " + escapeJson(scriptName) + "\"}");
            return;
        }
        File fakeFile = new File(new File("").getAbsoluteFile(), "synapmc-dashboard." + lang);
        final String[] error = new String[1];
        SynapMcShared.core.exec(lang, content, fakeFile, ex -> {
            String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            error[0] = msg;
        });
        if (error[0] != null) {
            sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(error[0]) + "\"}");
            return;
        }
        sendResponse(exchange, 200, "application/json", "{\"ok\":true}");
    }

    private void popular(HttpExchange exchange) throws IOException {

        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        Map<String, String> configuredRepos = SynapMcShared.core != null
                ? SynapMcShared.core.getConfiguredRepos()
                : new LinkedHashMap<String, String>();
        for (com.synapmc.model.RepoManifest manifest : RepoManager.getInstance().getManifests().values()) {
            if (manifest.popularRepos == null) continue;
            for (String url : manifest.popularRepos) {
                Map<String, Object> entry = new LinkedHashMap<String, Object>();
                entry.put("url", url);

                String name = url.replaceAll("[?#].*", "");
                int sl = name.lastIndexOf('/');
                if (sl >= 0) name = name.substring(0, sl);
                sl = name.lastIndexOf('/');
                if (sl >= 0) name = name.substring(sl + 1);
                entry.put("name", name.isEmpty() ? url : name);
                entry.put("added", configuredRepos.containsValue(url));
                result.add(entry);
            }
        }
        sendResponse(exchange, 200, "application/json; charset=utf-8", GSON.toJson(result));
    }

    private void favoritesApi(HttpExchange exchange) throws IOException {
        sendResponse(exchange, 200, "application/json; charset=utf-8",
                GSON.toJson(RepoManager.getInstance().getFavorites()));
    }

    private void setFavorite(HttpExchange exchange, boolean added) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> req = GSON.fromJson(body, Map.class);
            String name = req != null && req.get("name") != null ? req.get("name").toString() : null;
            if (name == null || name.isEmpty()) {
                sendResponse(exchange, 400, "application/json", "{\"error\":\"Missing name\"}");
                return;
            }
            if (added) RepoManager.getInstance().addFavorite(name);
            else RepoManager.getInstance().removeFavorite(name);
            sendResponse(exchange, 200, "application/json", "{\"ok\":true}");
        } catch (Exception e) {
            sendResponse(exchange, 500, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private synchronized void chooseFolder(HttpExchange exchange) throws IOException {
        try {
            @SuppressWarnings("unchecked") Map<String, Object> request = GSON.fromJson(
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), Map.class);
            String initial = request != null && request.get("path") instanceof String
                    ? (String) request.get("path") : "";
            Object selected = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs")
                    .getMethod("tinyfd_selectFolderDialog", CharSequence.class, CharSequence.class)
                    .invoke(null, "Choose download location", initial.isBlank() ? null : initial);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("path", selected);
            sendResponse(exchange, 200, "application/json", GSON.toJson(response));
        } catch (Exception | LinkageError error) {
            sendResponse(exchange, 500, "application/json", GSON.toJson(Map.of(
                    "error", "Could not open folder chooser. You can enter the location directly.")));
            SynapMcShared.error("[SynapMc] Folder chooser failed: " + error);
        }
    }

    private synchronized void installDirs(HttpExchange exchange) throws IOException {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            Map<String, String> values = new LinkedHashMap<String, String>();
            values.put("exe", SynapMcShared.config.exeDownloadDir);
            values.put("appimage", SynapMcShared.config.appimageDownloadDir);
            sendResponse(exchange, 200, "application/json; charset=utf-8", GSON.toJson(values));
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "text/plain", "Method Not Allowed"); return;
        }
        String previousExe = SynapMcShared.config.exeDownloadDir;
        String previousAppimage = SynapMcShared.config.appimageDownloadDir;
        try {
            @SuppressWarnings("unchecked") Map<String, Object> values = GSON.fromJson(
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), Map.class);
            SynapMcShared.config.exeDownloadDir = values != null && values.get("exe") != null
                    ? values.get("exe").toString().trim() : "";
            SynapMcShared.config.appimageDownloadDir = values != null && values.get("appimage") != null
                    ? values.get("appimage").toString().trim() : "";
            if (!SynapMcShared.saveConfig()) throw new IOException("Could not save settings");
            sendResponse(exchange, 200, "application/json", "{\"ok\":true}");
        } catch (Exception e) {
            SynapMcShared.config.exeDownloadDir = previousExe;
            SynapMcShared.config.appimageDownloadDir = previousAppimage;
            sendResponse(exchange, 400, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private void sendResponse(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        sendResponse(exchange, status, contentType, body.getBytes(StandardCharsets.UTF_8));
    }

    private void sendResponse(HttpExchange exchange, int status, String contentType, byte[] bytes) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String detectLang(String pathOrUrl) {
        if (pathOrUrl == null) return null;
        String clean = pathOrUrl.toLowerCase().replaceAll("[?#].*", "");
        if (clean.endsWith(".lua")) return "lua";
        if (clean.endsWith(".js")) return "js";
        if (clean.endsWith(".py")) return "py";
        return null;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
