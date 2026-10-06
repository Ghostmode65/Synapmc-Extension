package com.synapmc.library;

import com.synapmc.MinecraftAccess;
import com.synapmc.SynapMcShared;

import java.util.Locale;

public final class FClient {
    public String getOS() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac") || os.contains("darwin")) return "mac";
        if (os.contains("win")) return "windows";
        if (os.contains("linux")) return "linux";
        return "unknown";
    }

    public String jsVersion() { return SynapMcShared.core.getJsVersion(); }
    public boolean isFullscreen() { return MinecraftAccess.isFullscreen(); }
    public boolean toggleFullscreen(boolean enabled) { return MinecraftAccess.toggleFullscreen(enabled); }
}
