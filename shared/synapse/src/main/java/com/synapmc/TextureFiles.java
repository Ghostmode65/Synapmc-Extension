package com.synapmc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Validate inputs before changing either stack or item-wide rendering. */
final class TextureFiles {
    record Loaded(byte[] png, byte[] metadata, TextureAnimation animation) {}
    static Loaded load(Object pngFile, Object mcmetaFile) throws IOException {
        Path png = path(pngFile);
        byte[] bytes = Files.readAllBytes(png);
        if (bytes.length < 8 || !java.util.Arrays.equals(java.util.Arrays.copyOf(bytes, 8), new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10}))
            throw new IOException("Expected a PNG file: " + png);
        var image = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        if (image == null) throw new IOException("Could not decode PNG: " + png);
        Path metadataPath = mcmetaFile == null ? Path.of(png + ".mcmeta") : path(mcmetaFile);
        byte[] metadataBytes = null;
        JsonObject metadata = null;
        if (mcmetaFile != null || Files.isRegularFile(metadataPath)) {
            metadataBytes = Files.readAllBytes(metadataPath);
            try { metadata = JsonParser.parseString(new String(metadataBytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject(); }
            catch (RuntimeException error) { throw new IOException("Invalid texture metadata: " + metadataPath, error); }
        }
        try { return new Loaded(bytes, metadataBytes, new TextureAnimation(image, metadata)); }
        catch (RuntimeException error) { throw new IOException("Invalid texture animation", error); }
    }
    private static Path path(Object value) {
        if (value instanceof File file) return file.toPath();
        if (value instanceof Path path) return path;
        if (value instanceof String string) return Path.of(string);
        throw new IllegalArgumentException("Expected a PNG/metadata path or File");
    }
}
