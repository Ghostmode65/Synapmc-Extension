package com.synapmc;

import com.synapmc.library.FSynapse;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SynapMcSettingsUi {
    private SynapMcSettingsUi() {}

    public static Object get(String name) {
        SynapMcConfig config = SynapMcShared.config;
        if (config == null) config = new SynapMcConfig();
        switch (name) {
            case "autorunEnabled": return config.autorunEnabled;
            case "silent": return config.silent;
            case "tcpPortEnabled": return config.tcpPortEnabled;
            case "tcpPort": return config.tcpPort;
            case "tcpPortPassword": return config.tcpPortPassword;
            case "tcpBindAddress": return config.tcpBindAddress;
            case "clientInfoPreAuth": return config.clientInfoPreAuth;
            case "logExecuted": return config.logExecuted;
            case "downloadMaxMb": return config.downloadMaxMb;
            default: throw new IllegalArgumentException("Unknown SynapMc setting: " + name);
        }
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
        names.put("synapmc.settings.tcpPortEnabled", "Enable TCP bridge");
        names.put("synapmc.settings.tcpPort", "Port");
        names.put("synapmc.settings.tcpPortPassword", "Password");
        names.put("synapmc.settings.tcpBindAddress", "Bind address");
        names.put("synapmc.settings.clientInfoPreAuth", "Pre-authorization");
        names.put("synapmc.settings.logExecuted", "Log Executed");
        names.put("synapmc.settings.downloadMaxMb", "Download Cap (MiB)");
        return names;
    }
}
