package com.synapmc.library;

import com.synapmc.SynapMcConfig;
import com.synapmc.SynapMcShared;
import com.synapmc.JsMacrosSettings;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class FSynapse {
    private final Object runner;
    private final Object currentContext;

    public FSynapse(Object runner, Object currentContext) {
        this.runner = runner;
        this.currentContext = currentContext;
    }

    public int closeAllContexts(boolean keepThisScriptAlive) {
        int closed = closeOtherContexts();
        if (!keepThisScriptAlive && currentContext != null && !contextClosed(currentContext)) {
            CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS).execute(() -> closeContext(currentContext));
            closed++;
        }
        return closed;
    }

    private int closeOtherContexts() {
        int closed = 0;
        try {
            Object value = runner.getClass().getMethod("getContexts").invoke(runner);
            if (!(value instanceof Collection)) return 0;
            List<?> snapshot = new ArrayList<Object>((Collection<?>) value);
            for (Object context : snapshot) {
                if (context == null || context == currentContext || contextClosed(context)) continue;
                if (closeContext(context)) closed++;
            }
        } catch (Exception error) {
            SynapMcShared.error("[SynapMc] Could not enumerate script contexts: " + error);
        }
        return closed;
    }

    private static boolean contextClosed(Object context) {
        try { return Boolean.TRUE.equals(context.getClass().getMethod("isContextClosed").invoke(context)); }
        catch (Exception ignored) { return false; }
    }

    private static boolean closeContext(Object context) {
        try {
            context.getClass().getMethod("closeContext").invoke(context);
            return true;
        } catch (Exception error) {
            SynapMcShared.error("[SynapMc] Could not close script context: " + error);
            return false;
        }
    }

    public Map<String, Object> getConfig() {
        return synapMcConfig();
    }

    public Map<String, Object> getJsConfig() {
        return JsMacrosSettings.snapshot(SynapMcShared.core.getJsMacrosConfigManager());
    }

    public boolean saveJsConfig(String name, Object value) {
        return JsMacrosSettings.save(SynapMcShared.core.getJsMacrosConfigManager(), name, value);
    }

    public String roaming() {
        String path = SynapMcShared.roamingDir.getAbsolutePath();
        if (File.separatorChar == '\\') path = path.replace('\\', '/');
        return path.endsWith("/") ? path : path + "/";
    }

    private Map<String, Object> synapMcConfig() {
        SynapMcConfig cfg = SynapMcShared.config;
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("autorunEnabled", cfg.autorunEnabled);
        values.put("silent", cfg.silent);
        values.put("tcpPortEnabled", cfg.tcpPortEnabled);
        values.put("tcpPort", cfg.tcpPort);
        values.put("tcpPortPassword", cfg.tcpPortPassword);
        values.put("tcpBindAddress", cfg.tcpBindAddress);
        values.put("clientInfoPreAuth", cfg.clientInfoPreAuth);
        values.put("logExecuted", cfg.logExecuted);
        values.put("downloadMaxMb", cfg.downloadMaxMb);
        values.put("unifyFolders", new LinkedHashMap<String, String>(cfg.unifyFolders));
        values.put("tags", new LinkedHashMap<String, String>(cfg.tags));
        return values;
    }

    public boolean saveConfig(String settingName, Object value) {
        if (settingName == null) return false;
        Map<String, Object> settings = synapMcConfig();
        if (!settings.containsKey(settingName)) return false;
        SynapMcConfig cfg = SynapMcShared.config;
        Object previous = settings.get(settingName);
        boolean refreshTcp = false;
        boolean refreshUnify = false;
        switch (settingName) {
            case "autorunEnabled":
                if (!(value instanceof Boolean)) return false;
                cfg.autorunEnabled = (Boolean) value;
                break;
            case "silent":
                if (!(value instanceof Boolean)) return false;
                cfg.silent = (Boolean) value;
                break;
            case "tcpPortEnabled":
                if (!(value instanceof Boolean)) return false;
                cfg.tcpPortEnabled = (Boolean) value;
                refreshTcp = true;
                break;
            case "tcpPort":
                if (!(value instanceof Number)) return false;
                double requestedPort = ((Number) value).doubleValue();
                if (!Double.isFinite(requestedPort) || Math.rint(requestedPort) != requestedPort
                        || requestedPort < 1 || requestedPort > 65535) return false;
                int port = ((Number) value).intValue();
                cfg.tcpPort = port;
                refreshTcp = true;
                break;
            case "tcpPortPassword":
                if (!(value instanceof String)) return false;
                cfg.tcpPortPassword = (String) value;
                refreshTcp = true;
                break;
            case "tcpBindAddress":
                if (!(value instanceof String)) return false;
                cfg.tcpBindAddress = ((String) value).trim();
                refreshTcp = true;
                break;
            case "clientInfoPreAuth":
                if (!(value instanceof Boolean)) return false;
                cfg.clientInfoPreAuth = (Boolean) value;
                break;
            case "logExecuted":
                if (!(value instanceof Boolean)) return false;
                cfg.logExecuted = (Boolean) value;
                break;
            case "downloadMaxMb":
                if (!(value instanceof Number)) return false;
                double requestedMax = ((Number) value).doubleValue();
                if (!Double.isFinite(requestedMax) || Math.rint(requestedMax) != requestedMax
                        || requestedMax < 1 || requestedMax > 4096) return false;
                cfg.downloadMaxMb = ((Number) value).intValue();
                break;
            case "unifyFolders":
            case "tags":
                if (!(value instanceof Map<?, ?>)) return false;
                Map<String, String> folders = new LinkedHashMap<String, String>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof String)) return false;
                    folders.put((String) entry.getKey(), (String) entry.getValue());
                }
                refreshUnify = settingName.equals("unifyFolders");
                if (refreshUnify) cfg.unifyFolders = folders;
                else cfg.tags = folders;
                break;
            default:
                return false;
        }
        if (!SynapMcShared.saveConfig()) {
            restoreSetting(cfg, settingName, previous);
            return false;
        }
        if (refreshTcp) SynapMcShared.refreshTcp();
        if (refreshUnify) SynapMcShared.refreshUnifyFolders();
        return true;
    }

    @SuppressWarnings("unchecked")
    private void restoreSetting(SynapMcConfig cfg, String name, Object previous) {
        switch (name) {
            case "autorunEnabled": cfg.autorunEnabled = (Boolean) previous; break;
            case "silent": cfg.silent = (Boolean) previous; break;
            case "tcpPortEnabled": cfg.tcpPortEnabled = (Boolean) previous; break;
            case "tcpPort": cfg.tcpPort = (Integer) previous; break;
            case "tcpPortPassword": cfg.tcpPortPassword = (String) previous; break;
            case "tcpBindAddress": cfg.tcpBindAddress = (String) previous; break;
            case "clientInfoPreAuth": cfg.clientInfoPreAuth = (Boolean) previous; break;
            case "logExecuted": cfg.logExecuted = (Boolean) previous; break;
            case "downloadMaxMb": cfg.downloadMaxMb = (Integer) previous; break;
            case "unifyFolders": cfg.unifyFolders = (Map<String, String>) previous; break;
            case "tags": cfg.tags = (Map<String, String>) previous; break;
            default: break;
        }
    }
}
