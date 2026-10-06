package com.synapmc;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;

public final class ScriptDropService {
    private ScriptDropService() {}

    public static final class Entry {
        public final File source;
        public final String language;
        public final Path destination;
        public final String problem;
        public final int characters;
        public final String extension;

        private Entry(File source, String language, Path destination, String problem, int characters, String extension) {
            this.source = source;
            this.language = language;
            this.destination = destination;
            this.problem = problem;
            this.characters = characters;
            this.extension = extension;
        }
    }

    public static List<Entry> preview(List<File> files) {
        return preview(files, java.util.Collections.<String, String>emptyMap());
    }

    public static List<Entry> preview(List<File> files, Map<String, String> overrides) {
        return preview(files, overrides, java.util.Collections.emptyMap());
    }

    public static List<Entry> preview(List<File> files, Map<String, String> overrides, Map<String, String> names) {
        List<Entry> entries = new ArrayList<Entry>();
        if (files == null || SynapMcShared.core == null) return entries;
        Path destinationRoot = importRoot();
        Set<Path> reserved = new HashSet<Path>();
        Set<Path> seen = new LinkedHashSet<Path>();
        for (File file : files) {
            if (file == null) continue;
            Path source = file.toPath().toAbsolutePath().normalize();
            if (!seen.add(source)) continue;
            if (!Files.isRegularFile(source)) {
                entries.add(new Entry(file, null, null, "Not a regular file", 0, null));
                continue;
            }
            String language = null;
            try {
                language = SynapMcShared.core.getLanguageForFile(file);
            } catch (RuntimeException error) {
                language = null;
            }
            Map<String, String> installed = SynapMcShared.core.getAvailableLanguages();
            String requested = overrides == null ? null : overrides.get(source.toString());
            if (requested != null && installed.containsKey(requested)) language = requested;
            String extension = language == null ? null : installed.get(language);
            if (extension == null && language != null) {
                String name = file.getName(); int dot = name.lastIndexOf('.');
                if (dot >= 0 && dot + 1 < name.length()) extension = name.substring(dot + 1);
            }
            int characters;
            try { characters = new String(Files.readAllBytes(source), StandardCharsets.UTF_8).length(); }
            catch (IOException error) { characters = 0; }
            String destinationName = names.getOrDefault(source.toString(), file.getName());
            if (!validFilename(destinationName)) {
                entries.add(new Entry(file, language, null, "Enter a filename without folders or reserved characters", characters, extension));
                continue;
            }
            if (language != null && extension != null) {
                int dot = destinationName.lastIndexOf('.');
                String base = dot > 0 ? destinationName.substring(0, dot) : destinationName;
                destinationName = base + "." + extension;
            }
            Path destination = language == null
                    ? null : uniquePath(destinationRoot, destinationName, reserved);
            if (destination != null) reserved.add(destination);
            String problem = language == null ? "No installed JsMacros language accepts this file" : null;
            entries.add(new Entry(file, language, destination, problem, characters, extension));
        }
        return entries;
    }

    public static Path importFile(Entry entry) throws IOException {
        if (entry == null || entry.problem != null || entry.destination == null) {
            throw new IOException("The selected file cannot be imported");
        }
        Path root = importRoot();
        Files.createDirectories(root);
        if (!Files.isRegularFile(entry.source.toPath())) throw new IOException("Source file is unavailable");
        Path destination = entry.destination.toAbsolutePath().normalize();
        if (!destination.startsWith(root) || !destination.getParent().equals(root)) {
            throw new IOException("Import destination is outside global/imported");
        }
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            destination = uniquePath(root, destination.getFileName().toString(), new HashSet<Path>());
        }
        Files.copy(entry.source.toPath(), destination);
        return destination;
    }

    public static boolean executeFile(Entry entry) {
        if (entry == null || entry.problem != null || entry.language == null) return false;
        try {
            String script = new String(Files.readAllBytes(entry.source.toPath()), StandardCharsets.UTF_8);
            File executionFile = entry.extension == null ? entry.source
                    : new File(entry.source.getParentFile(), stripExtension(entry.source.getName()) + "." + entry.extension);
            SynapMcShared.core.exec(entry.language, script, executionFile, error ->
                    SynapMcShared.error("[SynapMc] Script failed: " + entry.source + ": " + error));
            return true;
        } catch (Exception error) {
            SynapMcShared.error("[SynapMc] Could not execute " + entry.source + ": " + error.getMessage());
            return false;
        }
    }

    public static boolean registerImportedScript(File file, String kind) throws Exception {
        if (file == null || SynapMcShared.core == null) return false;
        String relative = SynapMcShared.core.getMacroFolder().toPath().toAbsolutePath().normalize()
                .relativize(file.toPath().toAbsolutePath().normalize()).toString();
        if ("service".equals(kind)) {
            Object serviceTrigger = KeyBindingsTransfer.serviceTrigger(file, false);
            Class<?> serviceTriggerClass = serviceTrigger.getClass();
            Object services = SynapMcShared.core.getServices();
            if (services == null) return false;
            Object registered = services.getClass().getMethod("registerService", String.class, serviceTriggerClass)
                    .invoke(services, "", serviceTrigger);
            if (Boolean.TRUE.equals(registered)) {
                try { services.getClass().getMethod("save").invoke(services); } catch (NoSuchMethodException ignored) { }
                return true;
            }
            return false;
        }

        String triggerName = SynapMcShared.core.getScriptTriggerClassName();
        Class<?> triggerClass = Class.forName(triggerName);
        Class<?> triggerTypeClass = Class.forName(triggerName + "$TriggerType");
        @SuppressWarnings({"rawtypes", "unchecked"}) Object triggerType = Enum.valueOf((Class) triggerTypeClass,
                "event".equals(kind) ? "EVENT" : "KEY_RISING");
        Object trigger = triggerClass.getConstructor(triggerTypeClass, String.class, String.class,
                boolean.class, boolean.class).newInstance(triggerType, "", relative, false, false);
        Object profile = SynapMcShared.core.getProfile();
        if (profile == null) return false;
        Object registry = profile.getClass().getMethod("getRegistry").invoke(profile);
        registry.getClass().getMethod("addScriptTrigger", triggerClass).invoke(registry, trigger);
        profile.getClass().getMethod("saveProfile").invoke(profile);
        return true;
    }

    private static Path importRoot() {
        return SynapMcShared.core.getMacroFolder().toPath().toAbsolutePath().normalize()
                .resolve("global/imported").normalize();
    }

    private static boolean validFilename(String name) {
        return name != null && !name.isBlank() && !name.equals(".") && !name.equals("..")
                && !name.matches(".*[\\\\/:*?\"<>|\\p{Cntrl}].*") && !name.endsWith(".") && !name.endsWith(" ");
    }

    public static String displayName(Entry entry) {
        String original = entry.source.getName();
        String destination = entry.destination == null ? original : entry.destination.getFileName().toString();
        return original.equals(destination) ? original : original + " → " + destination;
    }

    private static Path uniquePath(Path root, String name, Set<Path> reserved) {
        String base = name;
        String extension = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            extension = name.substring(dot);
        }
        Path candidate = root.resolve(name);
        int index = 2;
        while (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS) || reserved.contains(candidate)) {
            candidate = root.resolve(base + " (" + index++ + ")" + extension);
        }
        return candidate;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
