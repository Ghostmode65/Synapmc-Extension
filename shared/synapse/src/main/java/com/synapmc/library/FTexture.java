package com.synapmc.library;

import com.google.gson.JsonParser;
import com.synapmc.SynapMcShared;
import com.synapmc.TextureAssets;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FTexture {
    private static final Map<String, Layers> layers = new LinkedHashMap<String, Layers>();
    private static final byte[] PNG_MAGIC = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    public Map<String, Object> get(Object itemStackHelper) {
        return TextureAssets.describeItem(itemStackHelper);
    }

    public List<String> export(Object itemStackHelper) throws IOException {
        return export(itemStackHelper, null);
    }

    public List<String> export(Object itemStackHelper, String path) throws IOException {
        Path root = TextureAssets.exportRoot().toAbsolutePath().normalize();
        Path destination = path == null || path.isEmpty() ? root : root.resolve(path).normalize();
        if (!destination.startsWith(root)) throw new IOException("Export path escapes assets/textures");
        Map<String, Object> info = get(itemStackHelper);
        List<String> exported = new ArrayList<String>();
        for (String category : new String[] {"png", "mcmeta", "model", "clientItem"}) {
            Object group = info.get(category);
            if (!(group instanceof List)) continue;
            for (Object item : (List<?>) group) {
                if (!(item instanceof Map)) continue;
                String id = String.valueOf(((Map<?, ?>) item).get("id"));
                TextureAssets.Asset asset = TextureAssets.read(id);
                if (asset == null) continue;
                Path file = destination.resolve("assets")
                        .resolve(id.substring(0, id.indexOf(':')))
                        .resolve(id.substring(id.indexOf(':') + 1)).normalize();
                if (!file.startsWith(destination)) throw new IOException("Resource path escapes export folder");
                Files.createDirectories(file.getParent());
                Files.write(file, asset.bytes);
                exported.add(file.toAbsolutePath().toString());
            }
        }
        return exported;
    }

    public TextureHandle spoof(String idOrItemName, String pngFile) throws IOException {
        return spoof(idOrItemName, pngFile, null);
    }

    public TextureHandle spoof(String idOrItemName, String pngFile, String mcmetaFile) throws IOException {
        String id = textureId(idOrItemName);
        byte[] png = loadFile(pngFile, 32 * 1024 * 1024);
        if (png.length < PNG_MAGIC.length || !Arrays.equals(Arrays.copyOf(png, PNG_MAGIC.length), PNG_MAGIC))
            throw new IOException("Replacement must be a PNG file");
        Map<String, byte[]> replacements = new LinkedHashMap<String, byte[]>();
        replacements.put(id, png);
        if (mcmetaFile != null && !mcmetaFile.isEmpty()) {
            byte[] metadata = loadFile(mcmetaFile, 1024 * 1024);
            try { JsonParser.parseString(new String(metadata, java.nio.charset.StandardCharsets.UTF_8)); }
            catch (RuntimeException error) { throw new IOException("Invalid .mcmeta JSON", error); }
            replacements.put(id + ".mcmeta", metadata);
        }
        TextureHandle handle = new TextureHandle(new ArrayList<String>(replacements.keySet()));
        synchronized (layers) {
            TextureAssets.ensurePack();
            try {
                for (Map.Entry<String, byte[]> entry : replacements.entrySet()) {
                    String resource = entry.getKey();
                    Layers state = layers.get(resource);
                    if (state == null) {
                        Path file = TextureAssets.packFile(resource);
                        state = new Layers(Files.exists(file) ? Files.readAllBytes(file) : null);
                        layers.put(resource, state);
                    }
                    state.active.add(new Layer(handle, entry.getValue()));
                    write(resource, entry.getValue());
                }
            } catch (IOException failure) {
                rollback(handle);
                throw failure;
            }
        }
        TextureAssets.activateAndReload();
        return handle;
    }

    private static String textureId(String name) throws IOException {
        if (name == null || name.trim().isEmpty()) throw new IOException("Texture ID or item name is required");
        String value = name.trim();
        if (value.contains("textures/") || value.endsWith(".png")) {
            if (value.indexOf(':') < 0) value = "minecraft:" + value;
            value = TextureAssets.normalizeId(value);
            if (!value.substring(value.indexOf(':') + 1).startsWith("textures/") || !value.endsWith(".png"))
                throw new IOException("Expected a texture resource ID such as minecraft:textures/item/stone.png");
            return value;
        }
        Map<String, Object> info = TextureAssets.describeItem(value);
        Object entries = info.get("png");
        if (!(entries instanceof List) || ((List<?>) entries).isEmpty())
            throw new IOException("No texture resource found for item " + value);
        if (((List<?>) entries).size() != 1)
            throw new IOException("Item " + value + " has multiple textures; use a resource ID from SExtra.texture.get()");
        return String.valueOf(((Map<?, ?>) ((List<?>) entries).get(0)).get("id"));
    }

    private static byte[] loadFile(String path, int limit) throws IOException {
        if (path == null || path.isEmpty()) throw new IOException("File path is required");
        File file = SynapMcShared.resolvePath(path);
        if (!file.isFile()) throw new IOException("File not found: " + file);
        if (file.length() > limit) throw new IOException("File is too large: " + file);
        return Files.readAllBytes(file.toPath());
    }

    private static void write(String id, byte[] bytes) throws IOException {
        Path target = TextureAssets.packFile(id);
        if (bytes == null) {
            Files.deleteIfExists(target);
            return;
        }
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".synapmc-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void rollback(TextureHandle handle) throws IOException {
        for (String id : handle.ids) {
            Layers state = layers.get(id);
            if (state == null) continue;
            state.active.removeIf(layer -> layer.owner == handle);
            byte[] current = state.active.isEmpty() ? state.baseline
                    : state.active.get(state.active.size() - 1).bytes;
            write(id, current);
            if (state.active.isEmpty()) layers.remove(id);
        }
    }

    private static final class Layers {
        final byte[] baseline;
        final List<Layer> active = new ArrayList<Layer>();
        Layers(byte[] baseline) { this.baseline = baseline; }
    }

    private static final class Layer {
        final TextureHandle owner;
        final byte[] bytes;
        Layer(TextureHandle owner, byte[] bytes) { this.owner = owner; this.bytes = bytes; }
    }

    public static final class TextureHandle {
        private final List<String> ids;
        private boolean active = true;
        private TextureHandle(List<String> ids) { this.ids = ids; }

        public List<String> getIds() { return new ArrayList<String>(ids); }
        public boolean isActive() { return active; }

        public boolean revert() {
            synchronized (layers) {
                if (!active) return false;
                try {
                    rollback(this);
                    active = false;
                } catch (IOException error) {
                    SynapMcShared.error("[SynapMc] Could not revert texture: " + error);
                    return false;
                }
            }
            TextureAssets.activateAndReload();
            return true;
        }
    }
}
