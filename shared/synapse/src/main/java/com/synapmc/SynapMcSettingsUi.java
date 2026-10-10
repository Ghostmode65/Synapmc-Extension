package com.synapmc;

import com.synapmc.library.FSynapse;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.Set;

public final class SynapMcSettingsUi {
    private SynapMcSettingsUi() {}

    public static Object get(String name) {
        SynapMcConfig config = SynapMcShared.config;
        if (config == null) config = new SynapMcConfig();
        switch (name) {
            case "autorunEnabled": return config.autorunEnabled;
            case "silent": return config.silent;
            case "chatSayEnabled": return config.chatSayEnabled;
            case "tcpPortEnabled": return config.tcpPortEnabled;
            case "tcpPort": return config.tcpPort;
            case "tcpPortPassword": return config.tcpPortPassword;
            case "tcpBindAddress": return config.tcpBindAddress;
            case "clientInfoPreAuth": return config.clientInfoPreAuth;
            case "logExecuted": return config.logExecuted;
            case "downloadMaxMb": return config.downloadMaxMb;
            case "unifyFolders": return unifiedFolders();
            default: throw new IllegalArgumentException("Unknown SynapMc setting: " + name);
        }
    }

    /** JsMacros' map editor mutates the getter result instead of calling a setter. */
    public static Map<String, String> unifiedFolders() {
        return new AbstractMap<String, String>() {
            private Map<String, String> snapshot() {
                return new LinkedHashMap<>(SynapMcShared.config.unifyFolders);
            }
            @Override public Set<Entry<String, String>> entrySet() {
                return Collections.unmodifiableMap(snapshot()).entrySet();
            }
            @Override public String put(String name, String directory) {
                Map<String, String> folders = snapshot();
                String previous = folders.put(name, directory);
                save(folders);
                return previous;
            }
            @Override public String remove(Object name) {
                Map<String, String> folders = snapshot();
                String previous = folders.remove(name);
                save(folders);
                return previous;
            }
            @Override public void clear() { save(new LinkedHashMap<>()); }
            private void save(Map<String, String> folders) {
                if (!new FSynapse(null, null).saveConfig("unifyFolders", folders))
                    throw new IllegalArgumentException("Could not save linked folders; use a folder name and a valid directory");
            }
        };
    }

    public static void set(String name, Object value) {
        if (!new FSynapse(null, null).saveConfig(name, value)) {
            SynapMcShared.error("[SynapMc] Could not save setting " + name);
        }
    }

    public static Map<String, String> translations(String language) {
        Map<String, String> names = new LinkedHashMap<String, String>();
        names.put("synapmc.settings", "SynapMc");
        names.put("synapmc.settings.tcp", "TCP Bridge");
        names.put("synapmc.settings.autorunEnabled", "Auto-execute");
        names.put("synapmc.settings.silent", "Silence Logs");
        names.put("synapmc.settings.chatSayEnabled", "Chat Say");
        names.put("synapmc.settings.tcpPortEnabled", "Enable TCP bridge");
        names.put("synapmc.settings.tcpPort", "Port");
        names.put("synapmc.settings.tcpPortPassword", "Password");
        names.put("synapmc.settings.tcpBindAddress", "Bind address");
        names.put("synapmc.settings.clientInfoPreAuth", "Pre-authorization");
        names.put("synapmc.settings.logExecuted", "Log Executed");
        names.put("synapmc.settings.downloadMaxMb", "Download Cap (MiB)");
        names.put("synapmc.settings.unifyFolders", "Unify");
        return names;
    }
}
