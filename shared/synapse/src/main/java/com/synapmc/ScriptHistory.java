package com.synapmc;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.*;

/** Immutable disk snapshots; timestamps mean first observed, never a guessed file age. */
public final class ScriptHistory {
    private static final long MINUTE = 60_000L, HOUR = 60 * MINUTE, DAY = 24 * HOUR;
    private static final long[] AGES = {10 * MINUTE, HOUR, DAY, 7 * DAY, 60 * DAY};
    private static final String[] LABELS = {"10 minutes ago", "1 hour ago", "1 day ago", "7 days ago", "60 days ago"};
    private final Path root;
    private final Clock clock;
    private static final Gson GSON = new Gson();

    public record Version(String label, long timestamp, Path snapshot) { }
    public ScriptHistory(Path scripts) { this(scripts, Clock.systemUTC()); }
    public ScriptHistory(Path scripts, Clock clock) {
        this.root = scripts.toAbsolutePath().normalize().resolve("backup");
        this.clock = clock;
    }

    public synchronized void capture(Path file) throws IOException {
        if (!Files.isRegularFile(file)) return;
        Path source = file.toRealPath();
        if (isBackup(source)) return;
        Path directory = directory(source);
        byte[] bytes = Files.readAllBytes(source);
        List<Version> versions = snapshots(directory);
        if (!versions.isEmpty() && Arrays.equals(bytes, Files.readAllBytes(versions.get(versions.size() - 1).snapshot()))) {
            compact(versions, clock.millis());
            return;
        }
        Files.createDirectories(directory);
        if (!Files.exists(directory.resolve("source.json"))) SynapMcConfig.write(directory.resolve("source.json"), source.toString());
        long timestamp = clock.millis();
        // A fast sequence of saves must keep the previous version, even within one millisecond.
        if (!versions.isEmpty()) timestamp = Math.max(timestamp, versions.get(versions.size() - 1).timestamp() + 1);
        // The shared directory can be used by multiple game instances. Unique names
        // prevent simultaneous captures from replacing one another.
        Path destination = directory.resolve(timestamp + "-" + UUID.randomUUID() + ".snapshot");
        Path temporary = Files.createTempFile(directory, "capture-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, destination); }
        } finally { Files.deleteIfExists(temporary); }
        versions.add(new Version("", timestamp, destination));
        compact(versions, clock.millis());
    }

    public synchronized List<Version> versions(Path source) throws IOException {
        Path identity = Files.exists(source) ? source.toRealPath() : source.toAbsolutePath().normalize();
        return select(snapshots(directory(identity)), clock.millis());
    }

    private List<Version> select(List<Version> versions, long now) {
        List<Version> result = new ArrayList<>();
        for (int i = 0; i < AGES.length; i++) {
            Version selected = null;
            for (Version version : versions) {
                if (version.timestamp() > now - AGES[i]) break;
                selected = version;
            }
            if (selected != null) result.add(new Version(LABELS[i], selected.timestamp(), selected.snapshot()));
        }
        return result;
    }

    private void compact(List<Version> versions, long now) throws IOException {
        if (versions.isEmpty()) return;
        Set<Path> keep = new HashSet<>();
        for (Version selected : select(versions, now)) keep.add(selected.snapshot());
        keep.add(versions.get(versions.size() - 1).snapshot());
        if (versions.size() > 1) keep.add(versions.get(versions.size() - 2).snapshot());
        // Keep both edges of each bucket so short bursts of saves do not erase
        // the version just before a change. At most about 300 snapshots per script.
        Map<String, Version> first = new HashMap<>(), last = new HashMap<>();
        Version older = null;
        for (Version version : versions) {
            long age = now - version.timestamp();
            if (age > 60 * DAY) { older = version; continue; }
            long span = age < HOUR ? MINUTE : age < DAY ? HOUR : DAY;
            String bucket = span + ":" + Math.floorDiv(version.timestamp(), span);
            first.putIfAbsent(bucket, version);
            last.put(bucket, version);
        }
        if (older != null) keep.add(older.snapshot());
        for (Version version : first.values()) keep.add(version.snapshot());
        for (Version version : last.values()) keep.add(version.snapshot());
        for (Version version : versions) if (!keep.contains(version.snapshot())) Files.deleteIfExists(version.snapshot());
    }

    /** Called once at game launch. Never removes trash during the running session. */
    public synchronized void onLaunch() throws IOException {
        Files.createDirectories(root);
        Path trash = root.resolve("trash");
        Files.createDirectories(trash);
        long now = clock.millis();
        try (var entries = Files.list(trash)) {
            for (Path entry : entries.toList()) {
                String name = entry.getFileName().toString();
                int dash = name.indexOf('-');
                if (dash < 1 || !Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) continue;
                try {
                    if (now - Long.parseLong(name.substring(0, dash)) >= 7 * DAY) deleteTree(entry);
                } catch (NumberFormatException ignored) { }
            }
        }
        try (var entries = Files.list(root)) {
            for (Path entry : entries.toList()) {
                if (!entry.getFileName().toString().matches("[0-9a-f]{64}")
                        || !Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) continue;
                try {
                    Path source = Path.of(GSON.fromJson(Files.readString(entry.resolve("source.json")), String.class));
                    // An inaccessible source is not evidence of deletion.
                    if (Files.notExists(source)) Files.move(entry, trash.resolve(now + "-" + entry.getFileName() + "-" + UUID.randomUUID()));
                    else compact(snapshots(entry), now);
                } catch (RuntimeException | IOException error) {
                    SynapMcShared.error("[SynapMc] Could not maintain history " + entry + ": " + error);
                }
            }
        }
    }

    public void scan(Collection<Path> directories, Set<String> extensions) throws IOException {
        Set<Path> visited = new HashSet<>();
        for (Path directory : directories) {
            if (!Files.isDirectory(directory)) continue;
            Files.walkFileTree(directory, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
                @Override public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) throws IOException {
                    Path real = path.toRealPath();
                    if (isBackup(real) || !visited.add(real)) return FileVisitResult.SKIP_SUBTREE;
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String name = file.getFileName().toString();
                    int dot = name.lastIndexOf('.');
                    if (attrs.isRegularFile() && dot >= 0 && extensions.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT))) {
                        try { capture(file); }
                        catch (IOException error) { SynapMcShared.error("[SynapMc] Could not back up " + file + ": " + error); }
                    }
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFileFailed(Path file, IOException error) {
                    if (!(error instanceof FileSystemLoopException)) SynapMcShared.error("[SynapMc] Could not scan " + file + ": " + error);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private boolean isBackup(Path real) throws IOException {
        return real.startsWith(Files.exists(root) ? root.toRealPath() : root);
    }

    private Path directory(Path source) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return root.resolve(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private List<Version> snapshots(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return new ArrayList<>();
        List<Version> result = new ArrayList<>();
        try (var entries = Files.list(directory)) {
            for (Path entry : entries.toList()) {
                String name = entry.getFileName().toString();
                if (!name.matches("[0-9]+(?:-[0-9a-f-]{36})?\\.snapshot") || !Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) continue;
                try { result.add(new Version("", Long.parseLong(name.split("[-.]", 2)[0]), entry)); }
                catch (NumberFormatException ignored) { }
            }
        }
        result.sort(Comparator.comparingLong(Version::timestamp).thenComparing(version -> version.snapshot().toString()));
        return result;
    }

    private static void deleteTree(Path directory) throws IOException {
        // Do not follow links out of the history trash.
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
