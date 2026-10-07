package com.synapmc.library;

import com.synapmc.SynapMcShared;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class FDownload {
    private final URI source;
    private final boolean overwrite;

    public FDownload(String url, boolean overwrite) {
        source = URI.create(url);
        if (!"https".equalsIgnoreCase(source.getScheme()) && !"http".equalsIgnoreCase(source.getScheme())) {
            throw new IllegalArgumentException("Download URL must use HTTP or HTTPS");
        }
        this.overwrite = overwrite;
    }

    public String mod() { return save(new File(SynapMcShared.core.getMinecraftDir(), "mods"), null); }
    public String extension() { return save(SynapMcShared.core.getExtensionFolder(), null); }
    public String macro() { return macro(null); }
    public String macro(String filepath) { return save(roaming("scripts/macros"), filepath); }
    public String macro(String filepath, String name) {
        return macro(destination(filepath, name));
    }
    public String script() { return script(null); }
    public String script(String filepath) { return save(roaming("scripts"), filepath); }
    public String script(String folder, String name) {
        return script(destination(folder, name));
    }
    public String image() { return image(null); }
    public String image(String filepath) { return save(roaming("assets/images"), filepath); }
    public String sound() { return sound(null); }
    public String sound(String filepath) { return save(roaming("assets/sounds"), filepath); }
    public String exe() { return save(executableDirectory(false), null); }
    public String appimage() { return save(executableDirectory(true), null); }

    private String destination(String folder, String name) {
        String selectedName = name == null || name.isEmpty() ? filename() : name;
        return folder == null || folder.isEmpty() ? selectedName : new File(folder, selectedName).getPath();
    }

    private static File roaming(String directory) { return new File(SynapMcShared.roamingDir, directory); }

    private static File executableDirectory(boolean appimage) {
        String configured = appimage ? SynapMcShared.config.appimageDownloadDir : SynapMcShared.config.exeDownloadDir;
        if (configured == null || configured.trim().isEmpty()) return new File(System.getProperty("user.home"), "Desktop");
        File folder = new File(configured.trim());
        return folder.isAbsolute() ? folder : new File(System.getProperty("user.home"), configured.trim());
    }

    private String save(File directory, String filepath) {
        try {
            String relative = filepath == null || filepath.isEmpty() ? filename() : filepath;
            if (relative.codePointCount(0, relative.length()) > 128) {
                throw new IOException("Destination filepath exceeds 128 characters");
            }
            Path root = directory.toPath().toAbsolutePath().normalize();
            Path target = root.resolve(relative).normalize();
            if (!target.startsWith(root) || target.equals(root)) throw new IOException("Destination leaves its download directory");
            if (Files.exists(target) && !overwrite) return null;
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), "synapmc-", ".tmp");
            try {
                URLConnection connection = source.toURL().openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(30000);
                long maxBytes = (long) SynapMcShared.config.downloadMaxMb * 1024L * 1024L;
                long declaredSize = connection.getContentLengthLong();
                if (declaredSize > maxBytes) throw new IOException("Download exceeds " + SynapMcShared.config.downloadMaxMb + " MiB limit");
                try (InputStream input = connection.getInputStream()) {
                    try (java.io.OutputStream output = Files.newOutputStream(temporary)) {
                        byte[] buffer = new byte[16384];
                        long written = 0;
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            written += count;
                            if (written > maxBytes) throw new IOException("Download exceeds " + SynapMcShared.config.downloadMaxMb + " MiB limit");
                            output.write(buffer, 0, count);
                        }
                    }
                }
                if (overwrite) Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                else Files.move(temporary, target);
            } finally {
                Files.deleteIfExists(temporary);
            }
            File downloaded = target.toFile();
            SynapMcShared.notifyDownload(downloaded);
            return downloaded.getAbsolutePath();
        } catch (Exception e) {
            SynapMcShared.error("[SynapMc] Download failed from " + source + ": " + e.getMessage());
            return null;
        }
    }

    private String filename() {
        String path = source.getPath();
        String name = path == null ? "" : path.substring(path.lastIndexOf('/') + 1);
        return name.isEmpty() ? "download.bin" : name;
    }
}
