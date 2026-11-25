package com.synapmc;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

/** Tokens are stored separately from workspace settings, with access limited to the file owner. */
final class GitCredentials {
    private final Path file;
    private volatile Map<String, String> tokens;
    GitCredentials(Path roaming) throws IOException {
        file = roaming.resolve("config/git-credentials.json");
        Map<String, String> loaded = Map.of();
        if (Files.exists(file)) {
            if (Files.isSymbolicLink(file)) throw new IOException("The Git credentials file must be a regular file.");
            protect(file);
            try {
                loaded = new Gson().fromJson(Files.readString(file), new TypeToken<Map<String, String>>() {}.getType());
                if (loaded == null) throw new IllegalArgumentException();
                for (var entry : loaded.entrySet()) { host(entry.getKey()); validate(entry.getValue()); }
            } catch (RuntimeException error) { throw new IOException("Could not read saved Git credentials."); }
        }
        tokens = Map.copyOf(loaded);
    }
    static void host(String host) throws IOException {
        if (!Set.of("github.com", "gitlab.com").contains(host)) throw new IOException("Choose GitHub or GitLab.");
    }
    static void validate(String token) throws IOException {
        if (token == null || !token.matches("[A-Za-z0-9_.~-]*")) throw new IOException("Enter an access token, or leave blank to clear it.");
    }
    String token(String host) { return tokens.getOrDefault(host, ""); }
    Path path() { return file; }
    synchronized void save(String host, String value) throws IOException {
        host(host); value = value.trim(); validate(value);
        Map<String, String> updated = new LinkedHashMap<>(tokens);
        if (value.isEmpty()) updated.remove(host); else updated.put(host, value);
        Files.createDirectories(file.getParent());
        Path temporary;
        if (Files.getFileStore(file.getParent()).supportsFileAttributeView(PosixFileAttributeView.class))
            temporary = Files.createTempFile(file.getParent(), "git-credentials-", ".tmp",
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        else temporary = Files.createTempFile(file.getParent(), "git-credentials-", ".tmp");
        try {
            protect(temporary);
            Files.writeString(temporary, new Gson().toJson(updated));
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException error) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
            tokens = Map.copyOf(updated);
        } finally { Files.deleteIfExists(temporary); }
    }
    private static void protect(Path file) throws IOException {
        PosixFileAttributeView posix = Files.getFileAttributeView(file, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (posix != null) { posix.setPermissions(PosixFilePermissions.fromString("rw-------")); return; }
        AclFileAttributeView acl = Files.getFileAttributeView(file, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (acl != null) {
            acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(acl.getOwner())
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
            return;
        }
        throw new IOException("This filesystem cannot restrict access to saved tokens.");
    }
}
