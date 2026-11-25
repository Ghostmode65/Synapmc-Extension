package com.synapmc;

import com.google.gson.*;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.*;

/** Updates local manifest membership while preserving user-authored JSON fields. */
public final class GitProjectManifest {
    private GitProjectManifest() { }
    public record Update(boolean created, int added, int removed) { }

    static boolean localFile(Path root, String relative) {
        try {
            Path path = Path.of(relative);
            if (path.isAbsolute() || !root.resolve(path).normalize().startsWith(root)) return false;
            Path current = root;
            for (Path part : path) {
                current = current.resolve(part);
                if (Files.isSymbolicLink(current)) return false;
            }
            return Files.isRegularFile(current, LinkOption.NOFOLLOW_LINKS);
        } catch (InvalidPathException error) { return false; }
    }

    static Update update(Path file, GitHubProjects.Project project, String branch, String author, SortedSet<String> scripts,
                         Set<String> omitted) throws IOException {
        boolean created = !Files.exists(file, LinkOption.NOFOLLOW_LINKS);
        JsonObject manifest;
        try {
            if (!created && !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("manifest.json must be a regular file.");
            manifest = created ? new JsonObject() : JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (manifest.has("scripts") && !manifest.get("scripts").isJsonArray())
                throw new IOException("manifest.json must contain a scripts array.");
        } catch (RuntimeException error) { throw new IOException("Could not read manifest.json. Correct its JSON before updating.", error); }
        if (created) {
            manifest.addProperty("name", project.name().substring(project.name().lastIndexOf('/') + 1));
            manifest.addProperty("source", project.remote().substring(0, project.remote().length() - 4));
            manifest.addProperty("author", author == null || author.isBlank() ? project.name().split("/")[0] : author);
        }
        JsonArray entries = new JsonArray();
        Set<String> represented = new HashSet<>(), names = new HashSet<>();
        int removed = 0, added = 0;
        if (manifest.has("scripts")) for (JsonElement element : manifest.getAsJsonArray("scripts")) {
            if (!element.isJsonObject()) throw new IOException("Each manifest script must be an object. Correct the JSON before updating.");
            JsonObject entry = element.getAsJsonObject();
            String path = entryPath(entry, project, branch);
            if (path != null && (path.equals(".ignore") || path.startsWith(".ignore/") || path.equals(".private")
                    || path.startsWith(".private/") || (!localFile(project.path(), path) && !omitted.contains(path)))) { removed++; continue; }
            // Entries from external URLs are preserved; their files are not in this checkout.
            entries.add(entry);
            if (path != null) represented.add(path);
            String name = string(entry, "name");
            if (name != null) names.add(name);
        }
        for (String path : scripts) {
            if (represented.contains(path)) continue;
            String name = path.substring(path.lastIndexOf('/') + 1, path.lastIndexOf('.'));
            if (names.contains(name)) name = path.substring(0, path.lastIndexOf('.'));
            if (names.contains(name)) name = path;
            JsonObject entry = new JsonObject();
            entry.addProperty("path", path);
            entry.addProperty("name", name);
            entry.addProperty("author", author == null || author.isBlank() ? project.name().split("/")[0] : author);
            entry.addProperty("source", source(project, branch, path));
            entry.addProperty("destination", "scripts/macros/");
            entry.addProperty("version", "1.0");
            entry.addProperty("category", "macro");
            entries.add(entry); names.add(name); added++;
        }
        manifest.add("scripts", entries);
        SynapMcConfig.write(file, manifest);
        return new Update(created, added, removed);
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }
    private static String entryPath(JsonObject entry, GitHubProjects.Project project, String branch) {
        String path = string(entry, "path");
        if (path != null) return path;
        String source = string(entry, "source");
        if (source == null) return null;
        try {
            // Older generated manifests left spaces in raw URLs unescaped.
            URI uri = URI.create(source.replace(" ", "%20").replace("\t", "%09").replace("\n", "%0A").replace("\r", "%0D"));
            if (uri.getScheme() == null) return uri.getPath().replaceFirst("^\\./", "");
            String prefix = null;
            if (project.host().equals("github.com") && "raw.githubusercontent.com".equalsIgnoreCase(uri.getHost()))
                prefix = "/" + project.name() + "/";
            if (project.host().equals("gitlab.com") && "gitlab.com".equalsIgnoreCase(uri.getHost()))
                prefix = "/" + project.name() + "/-/raw/";
            if (prefix == null || uri.getPath() == null || !uri.getPath().startsWith(prefix)) return null;
            String remaining = uri.getPath().substring(prefix.length());
            if (remaining.startsWith(branch + "/")) return remaining.substring(branch.length() + 1);
            int slash = remaining.indexOf('/');
            return slash < 0 ? null : remaining.substring(slash + 1);
        } catch (IllegalArgumentException error) { return null; }
    }
    static String source(GitHubProjects.Project project, String branch, String path) throws IOException {
        String host = project.host().equals("gitlab.com") ? "gitlab.com" : "raw.githubusercontent.com";
        String prefix = "/" + project.name() + (project.host().equals("gitlab.com") ? "/-/raw/" : "/");
        try { return new URI("https", host, prefix + branch + "/" + path, null).toASCIIString(); }
        catch (java.net.URISyntaxException error) { throw new IOException("Could not create the script download URL.", error); }
    }
}
