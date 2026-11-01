package com.synapmc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class SnippetStore {
    private final Path root;

    public SnippetStore(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }

    public static SnippetStore open() throws IOException {
        Path home = SynapMcShared.roamingDir.toPath();
        return new SnippetStore(home.resolve("scripts/snippets"));
    }

    public static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot).toLowerCase(Locale.ROOT);
    }

    public List<Path> list(String extension) throws IOException {
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).filter(path -> allowed(path))
                    .filter(path -> !extension(path.getFileName().toString()).isEmpty())
                    .filter(path -> extension == null || extension(path.toString()).equalsIgnoreCase(extension))
                    .sorted(Comparator.comparing(this::name, String.CASE_INSENSITIVE_ORDER)).toList();
        }
    }

    private boolean allowed(Path path) {
        try { return path.toRealPath().startsWith(root.toRealPath()); }
        catch (IOException error) { return false; }
    }

    private void check(Path path) throws IOException {
        if (!path.toAbsolutePath().normalize().startsWith(root) || !allowed(path) || !Files.isRegularFile(path))
            throw new IOException("Snippet is outside the snippets folder");
    }

    public String name(Path path) { return root.relativize(path.toAbsolutePath().normalize()).toString(); }
    public String read(Path path) throws IOException { check(path); return Files.readString(path, StandardCharsets.UTF_8); }
    public Path create(String name) throws IOException {
        name = filename(name);
        Path path = root.resolve(name);
        Files.writeString(path, "", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        return path;
    }

    public Path rename(Path path, String name) throws IOException {
        check(path);
        Path renamed = path.resolveSibling(filename(name));
        if (renamed.equals(path)) return path;
        // Never replace another snippet or a dangling symlink.
        if (Files.exists(renamed, java.nio.file.LinkOption.NOFOLLOW_LINKS))
            throw new IOException("A snippet with that name already exists");
        return Files.move(path, renamed);
    }

    public void delete(Path path) throws IOException { check(path); Files.delete(path); }

    private static String filename(String name) throws IOException {
        name = name.trim();
        if (!name.matches("[\\p{L}\\p{N}_ .-]+") || name.startsWith(".") || extension(name).length() <= 1)
            throw new IOException("Use a filename with a language extension, e.g. example.js or example.lua");
        return name;
    }
}
