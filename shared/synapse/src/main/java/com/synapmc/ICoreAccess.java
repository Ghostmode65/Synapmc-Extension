package com.synapmc;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.function.Consumer;

public interface ICoreAccess {
    void exec(String language, String script, File file, Consumer<Throwable> onError);
    File getMacroFolder();
    File getExtensionFolder();
    File getMinecraftDir();
    File captureMinecraftScreenshot(String filename) throws Exception;
    Map<String, Object> getClientInfo();
    boolean isClientInfoPreAuth();
    Object getJsMacrosConfigManager();
    String getJsVersion();
    String getLanguageForFile(File file);
    default Map<String, String> getAvailableLanguages() { return java.util.Collections.emptyMap(); }
    Object createScriptDropScreen();
    default Object getServices() { return null; }

    default byte[] httpGet(String address) throws IOException {
        URL url = new URL(address);
        String protocol = url.getProtocol();
        if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
            throw new IOException("Only HTTP and HTTPS repository URLs are supported");
        }
        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        long maxBytes = (long) SynapMcShared.config.downloadMaxMb * 1024L * 1024L;
        long declaredSize = connection.getContentLengthLong();
        if (declaredSize > maxBytes) throw new IOException("Response exceeds configured download limit");
        try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > maxBytes) throw new IOException("Response exceeds configured download limit");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    default String httpGetText(String address) throws IOException {
        return new String(httpGet(address), java.nio.charset.StandardCharsets.UTF_8);
    }

    default boolean isAutoRegisterTriggers() { return false; }
    default boolean hasExtensionForFile(File file) { return getLanguageForFile(file) != null; }
    default String getScriptTriggerClassName() { return null; }
    default Object getProfile() { return null; }
    default int getTcpPort() { return SynapMcShared.config.tcpPort; }
    default String getTcpPortPassword() { return SynapMcShared.config.tcpPortPassword; }
    default Map<String, String> getConfiguredRepos() { return SynapMcShared.config.repos; }
    default void addConfigRepo(String name, String address) {
        SynapMcShared.config.repos.put(name, address);
        SynapMcShared.saveConfig();
    }
    default void removeConfigRepo(String name) {
        SynapMcShared.config.repos.remove(name);
        SynapMcShared.saveConfig();
    }
    default void saveConfig() { SynapMcShared.saveConfig(); }
    default Map<String, String> getUnifyFolders() { return SynapMcShared.config.unifyFolders; }
    default Map<String, String> getPathTags() { return SynapMcShared.config.tags; }
    default String getJsMacrosApiVersion() {
        String version = getJsVersion();
        if ("community".equalsIgnoreCase(version)) return "2.0.0";
        if ("legacy".equalsIgnoreCase(version)) return "1.9.2";
        return version;
    }
}
