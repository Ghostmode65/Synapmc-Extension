package com.synapmc;

import java.util.Locale;

/** Implementation shared by the Client mixins for each edition. */
public final class ClientMethods {
    private ClientMethods() {}

    public static String getOS() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac") || os.contains("darwin")) return "mac";
        if (os.contains("win")) return "windows";
        if (os.contains("linux")) return "linux";
        return "unknown";
    }

    public static String jsVersion() { return SynapMcShared.core.getJsVersion(); }
    public static boolean isFullscreen() { return MinecraftAccess.isFullscreen(); }
    public static boolean toggleFullscreen(boolean enabled) { return MinecraftAccess.toggleFullscreen(enabled); }
}
