package com.synapmc;

import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Portable profiles shared by all editions; native config remains the live GUI state. */
public final class SharedProfiles {
    private static final Gson GSON = new Gson();
    private static volatile State state;

    public record Binding(String type, String event, String file, boolean enabled, boolean joined) {}
    public static final class Archive {
        public int version = 1;
        public String startup = "default";
        public Map<String, List<Binding>> profiles = new LinkedHashMap<>();
        public Set<String> sources = new LinkedHashSet<>();
    }
    private static final class State {
        final Object manager;
        final Path file;
        Archive baseline;
        volatile boolean ready;
        State(Object manager, Path file) { this.manager = manager; this.file = file; }
    }

    private SharedProfiles() {}

    public static Path path() { return SynapMcShared.roamingDir.toPath().resolve("config/profiles/profiles.json"); }

    /** Called during extension initialization, before JsMacros loads its startup profile. */
    public static void initialize() throws Exception {
        NativeProfiles.Access access = NativeProfiles.access();
        synchronized (access.manager()) {
            State next = new State(access.manager(), path());
            state = next;
            locked(next, () -> {
                Archive local = snapshot(access, null);
                if (local.profiles.isEmpty()) local.profiles.put("default", new ArrayList<>());
                boolean existing = Files.exists(next.file);
                Archive shared = existing ? read(next.file) : local;
                String source = ((File) access.manager().getClass().getField("configFile").get(access.manager()))
                        .getCanonicalPath();
                if (shared.sources.add(source)) {
                    // Migrate each native config once. Returning to an older edition must
                    // not resurrect names deleted or renamed in the shared store.
                    for (var entry : local.profiles.entrySet()) {
                        String name = entry.getKey();
                        if (existing && name.equals("default") && entry.getValue().isEmpty()) continue;
                        if (shared.profiles.containsKey(name)) {
                            if (entry.getValue().isEmpty() || entry.getValue().equals(shared.profiles.get(name))) continue;
                            String suffix = " (" + SynapMcShared.core.getJsVersion() + ")";
                            name += suffix;
                            while (shared.profiles.containsKey(name)) name += suffix;
                        }
                        shared.profiles.put(name, entry.getValue());
                    }
                }
                if (!shared.profiles.containsKey(shared.startup)) shared.startup = first(shared.profiles);
                Map<String, List<Object>> converted = toNative(shared);
                Map<String, List<Object>> previous = new LinkedHashMap<>(access.profiles());
                String startup = access.startup();
                try {
                    SynapMcConfig.write(next.file, shared);
                    access.profiles().clear(); access.profiles().putAll(converted);
                    access.startup(shared.startup);
                    access.manager().getClass().getMethod("saveConfig").invoke(access.manager());
                    access.verifySaved();
                } catch (Exception error) {
                    access.profiles().clear(); access.profiles().putAll(previous); access.startup(startup);
                    throw error;
                }
                next.baseline = shared; next.ready = true;
            });
        }
    }

    /** Native Settings and Keys also save through this hook, not just the ⇄ page. */
    public static void nativeSaved(Object manager) {
        State current = state;
        if (current == null || current.manager != manager || !current.ready) return;
        try {
            NativeProfiles.Access access = NativeProfiles.access();
            synchronized (manager) { access.verifySaved(); save(access); }
        } catch (Exception error) { SynapMcShared.error("[SynapMc] Could not sync profiles: " + error.getMessage()); }
    }

    static void save(NativeProfiles.Access access) throws Exception {
        State current = state;
        if (current == null || current.manager != access.manager() || !current.ready) return;
        locked(current, () -> {
            Archive local = snapshot(access, current.baseline);
            Archive latest = read(current.file);
            // Merge only this instance's edits, retaining another running edition's
            // additions and deletions when our copy of that profile did not change.
            Set<String> names = new LinkedHashSet<>(current.baseline.profiles.keySet());
            names.addAll(local.profiles.keySet());
            for (String name : names) {
                if (Objects.equals(local.profiles.get(name), current.baseline.profiles.get(name))) continue;
                if (local.profiles.containsKey(name)) latest.profiles.put(name, local.profiles.get(name));
                else latest.profiles.remove(name);
            }
            if (!Objects.equals(local.startup, current.baseline.startup)) latest.startup = local.startup;
            if (latest.profiles.isEmpty()) throw new IOException("Keep at least one shared profile.");
            if (!latest.profiles.containsKey(latest.startup)) latest.startup = first(latest.profiles);
            if (!GSON.toJson(latest).equals(GSON.toJson(read(current.file)))) SynapMcConfig.write(current.file, latest);
            current.baseline = local;
        });
    }

    private static Archive snapshot(NativeProfiles.Access access, Archive previous) throws Exception {
        Archive archive = new Archive(); archive.startup = access.startup();
        Path root = SynapMcShared.core.getMacroFolder().toPath().toAbsolutePath().normalize();
        for (var entry : access.profiles().entrySet()) {
            List<Binding> rows = new ArrayList<>();
            for (Object trigger : entry.getValue()) {
                Class<?> type = trigger.getClass();
                String kind = type.getField("triggerType").get(trigger).toString();
                String event = (String) type.getField("event").get(trigger);
                Object file = type.getField("scriptFile").get(trigger);
                Path path = file instanceof Path p ? p : file instanceof File f ? f.toPath() : Path.of(file.toString());
                String absolute = (path.isAbsolute() ? path : root.resolve(path)).normalize().toString();
                boolean enabled = type.getField("enabled").getBoolean(trigger);
                boolean joined;
                try { joined = type.getField("joined").getBoolean(trigger); }
                catch (NoSuchFieldException legacy) {
                    // Old Legacy does not expose joined; keep the newer edition's flag.
                    List<Binding> prior = previous == null ? List.of() : previous.profiles.containsKey(entry.getKey())
                            ? previous.profiles.get(entry.getKey()) : previous.profiles.values().stream().flatMap(List::stream).toList();
                    joined = prior.stream()
                            .anyMatch(row -> row.type().equals(kind) && row.event().equals(event) && row.file().equals(absolute) && row.joined());
                }
                rows.add(new Binding(kind, event, absolute, enabled, joined));
            }
            archive.profiles.put(entry.getKey(), rows);
        }
        return archive;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, List<Object>> toNative(Archive archive) throws Exception {
        Class<?> trigger = Class.forName(SynapMcShared.core.getScriptTriggerClassName());
        Class<? extends Enum> kind = (Class<? extends Enum>) Class.forName(trigger.getName() + "$TriggerType");
        boolean relativePaths = trigger.getField("scriptFile").getType() == String.class;
        Path root = SynapMcShared.core.getMacroFolder().toPath().toAbsolutePath().normalize();
        Map<String, List<Object>> profiles = new LinkedHashMap<>();
        for (var entry : archive.profiles.entrySet()) {
            List<Object> rows = new ArrayList<>();
            for (Binding row : entry.getValue()) {
                Object type = Enum.valueOf(kind, row.type());
                // Old Legacy joins String paths to its own macro folder using File.
                // Its runtime needs a relative path, even for a script outside that folder.
                String file = relativePaths ? root.relativize(Path.of(row.file())).toString() : row.file();
                try { rows.add(trigger.getConstructor(kind, String.class, String.class, boolean.class, boolean.class)
                        .newInstance(type, row.event(), file, row.enabled(), row.joined())); }
                catch (NoSuchMethodException legacy) {
                    rows.add(trigger.getConstructor(kind, String.class, String.class, boolean.class)
                            .newInstance(type, row.event(), file, row.enabled()));
                }
            }
            profiles.put(entry.getKey(), rows);
        }
        return profiles;
    }

    private static Archive read(Path file) throws IOException {
        Archive archive;
        try { archive = GSON.fromJson(Files.readString(file), Archive.class); }
        catch (RuntimeException error) { throw new IOException("Invalid shared profiles file: " + file, error); }
        if (archive == null || archive.version != 1 || archive.profiles == null || archive.profiles.isEmpty()
                || archive.sources == null || archive.startup == null) throw new IOException("Invalid shared profiles file: " + file);
        for (var entry : archive.profiles.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) throw new IOException("Invalid shared profile.");
            for (Binding row : entry.getValue()) {
                if (row == null || row.type() == null || row.event() == null || row.file() == null || !Path.of(row.file()).isAbsolute())
                    throw new IOException("Invalid shared binding in " + entry.getKey());
            }
        }
        return archive;
    }

    private static String first(Map<String, ?> profiles) {
        return profiles.containsKey("default") ? "default" : profiles.keySet().stream().sorted().findFirst().orElseThrow();
    }

    private interface Change { void apply() throws Exception; }
    private static void locked(State state, Change change) throws Exception {
        Files.createDirectories(state.file.getParent());
        try (FileChannel channel = FileChannel.open(state.file.resolveSibling("profiles.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE); var lock = channel.lock()) { change.apply(); }
    }
}
