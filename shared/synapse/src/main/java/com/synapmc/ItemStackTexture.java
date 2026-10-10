package com.synapmc;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Item texture discovery/export, with an optional private replacement for JsMacros GUI items. */
public final class ItemStackTexture {
    private final Supplier<Object> stack;
    private final Consumer<Object> detach;
    private final TextureBackend backend;
    private Object privateStack;

    public ItemStackTexture(Supplier<Object> stack, Consumer<Object> detach) {
        this(stack, detach, NativeTextureAccess.INSTANCE);
    }
    public ItemStackTexture(Supplier<Object> stack, Consumer<Object> detach, TextureBackend backend) {
        this.stack = stack; this.detach = detach; this.backend = backend;
    }

    /** Distinct active PNG resource identifiers; particle sprites and inactive variants are excluded. */
    public List<String> getIdentifiers() { return List.copyOf(new LinkedHashSet<>(backend.identifiers(stack.get()))); }

    public List<File> export() throws IOException {
        return exportAssets(backend, getIdentifiers());
    }
    static List<File> exportAssets(TextureBackend backend, List<String> identifiers) throws IOException {
        if (SynapMcShared.roamingDir == null) throw new IOException("SynapMc has not initialized its asset directory");
        Path root = SynapMcShared.roamingDir.toPath().resolve("assets/textures/assets");
        List<File> output = new ArrayList<>();
        for (String identifier : identifiers) {
            byte[] png = backend.readAsset(identifier);
            if (png == null) throw new IOException("No PNG resource exists for active item texture " + identifier);
            output.add(write(root, identifier, png));
            byte[] metadata = backend.readAsset(identifier + ".mcmeta");
            if (metadata != null) output.add(write(root, identifier + ".mcmeta", metadata));
        }
        return List.copyOf(output);
    }

    public ItemStackTexture replace(Object pngFile) throws IOException { return replace(pngFile, null); }
    public synchronized ItemStackTexture replace(Object pngFile, Object mcmetaFile) throws IOException {
        TextureAnimation animation = TextureFiles.load(pngFile, mcmetaFile).animation();
        Object current = stack.get();
        if (current != privateStack) {
            Object copy = backend.copyStack(current);
            if (copy == null || copy == current) throw new IllegalStateException("Item stack was not copied for its private texture");
            detach.accept(copy);
            privateStack = copy;
        }
        ItemTextureOverrides.set(privateStack, animation, backend);
        return this;
    }

    public synchronized ItemStackTexture revert() {
        if (privateStack != null) ItemTextureOverrides.remove(privateStack);
        return this;
    }

    private static File write(Path root, String identifier, byte[] data) throws IOException {
        String[] id = identifier.split(":", 2);
        if (id.length != 2 || !id[0].matches("[a-z0-9_.-]+") || !id[1].matches("[a-z0-9/._-]+")
                || id[1].startsWith("/") || java.util.Arrays.asList(id[1].split("/")).contains(".."))
            throw new IOException("Invalid texture resource identifier: " + identifier);
        Path destination = root.toAbsolutePath().normalize().resolve(id[0]).resolve(id[1]).normalize();
        if (!destination.startsWith(root.toAbsolutePath().normalize())) throw new IOException("Texture export leaves its asset directory");
        // Reject existing links, including the output file, before creating or overwriting assets.
        for (Path ancestor = destination; ancestor != null; ancestor = ancestor.getParent())
            if (Files.isSymbolicLink(ancestor)) throw new IOException("Texture export path contains a symbolic link: " + ancestor);
        Files.createDirectories(destination.getParent());
        Files.write(destination, data);
        return destination.toFile();
    }
}
