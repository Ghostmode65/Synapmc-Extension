package com.synapmc;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.*;
import java.net.URI;
import java.util.*;

/** Persistent project links; repository work runs only in response to a page action. */
public final class GitHubProjects {
    public record Project(String remote, String directory, boolean managed) {
        public String name() {
            String path = URI.create(remote).getPath();
            return path.substring(1, path.length() - 4);
        }
        public String host() { return URI.create(remote).getHost(); }
        public String provider() { return host().equals("gitlab.com") ? "GitLab" : "GitHub"; }
        public Path path() { return Path.of(directory); }
    }
    public record Workspace(String name, String directory) { }
    public record Change(String kind, String path) { }
    public record Review(Project project, String branch, String head, String base, String tree, int ahead, List<Change> changes) { }
    public record RefreshResult(Project project, Review review, String error) { }
    private static final class State {
        List<Project> projects = new ArrayList<>();
        List<Workspace> workspaces = new ArrayList<>();
        volatile String authorName = "", authorEmail = "";
        Map<String, String> tokenNotes = new LinkedHashMap<>();
        Map<String, Boolean> tokenWarnings = new LinkedHashMap<>();
    }
    private static GitHubProjects instance;
    private final Path roaming, config;
    private final GitHubGit git;
    private State state;
    private final GitCredentials credentials;
    private final GitTokenPermissions permissions;
    private volatile Map<String, String> tokenNotes = Map.of();
    private volatile Map<String, Boolean> tokenWarnings = Map.of();
    private volatile List<Project> projects = List.of();
    private volatile List<Workspace> workspaces = List.of();

    public static synchronized GitHubProjects open() throws IOException {
        Path root = SynapMcShared.roamingDir.toPath().toAbsolutePath().normalize();
        if (Files.exists(root)) root = root.toRealPath();
        if (instance == null || !instance.roaming.equals(root)) instance = new GitHubProjects(root, new GitHubGit());
        return instance;
    }
    GitHubProjects(Path roaming, GitHubGit git) throws IOException {
        this(roaming, git, new GitTokenPermissions());
    }
    GitHubProjects(Path roaming, GitHubGit git, GitTokenPermissions permissions) throws IOException {
        this.roaming = Files.exists(roaming) ? roaming.toRealPath() : roaming.toAbsolutePath().normalize(); this.git = git;
        this.permissions = permissions;
        credentials = new GitCredentials(this.roaming);
        config = this.roaming.resolve("config/github-workspaces.json");
        try { state = Files.exists(config) ? new Gson().fromJson(Files.readString(config), State.class) : new State(); }
        catch (RuntimeException error) { throw new IOException("Could not read GitHub workspace settings."); }
        if (state == null || state.projects == null || state.workspaces == null) throw new IOException("Invalid GitHub workspace settings.");
        if (state.tokenNotes == null) state.tokenNotes = new LinkedHashMap<>();
        if (state.tokenWarnings == null) state.tokenWarnings = new LinkedHashMap<>();
        publish();
        syncUnifiedFolder();
    }
    public List<Project> projects() { return projects; }
    public List<Workspace> workspaces() { return workspaces; }
    public boolean hasToken() { return hasToken("github.com"); }
    public boolean hasToken(String host) { return !credentials.token(host).isEmpty(); }
    public void token(String value) throws IOException { token("github.com", value); }
    public synchronized void token(String host, String value) throws IOException {
        credentials.save(host, value);
        state.tokenNotes.remove(host); state.tokenWarnings.remove(host); save();
    }
    public String tokenNote(String host) { return tokenNotes.getOrDefault(host, hasToken(host) ? "Token saved locally for future sessions." : "No token saved."); }
    public boolean tokenWarning(String host) { return tokenWarnings.getOrDefault(host, false); }
    public String authorEmail() { return state.authorEmail; }
    public String authorName() { return state.authorName; }
    public GitTokenPermissions.Check checkToken(String host) throws IOException {
        GitCredentials.host(host);
        String token = credentials.token(host);
        GitTokenPermissions.Check result = permissions.inspect(host, token);
        synchronized (this) {
            if (credentials.token(host).equals(token)) {
                state.tokenNotes.put(host, result.message()); state.tokenWarnings.put(host, result.warning()); save();
            }
        }
        return result;
    }
    private GitHubGit.Credential credential(String remote) { String host = URI.create(remote).getHost(); return new GitHubGit.Credential(host, credentials.token(host)); }
    public synchronized void identity(String name, String email) throws IOException {
        if (name.isBlank() || email.isBlank() || name.matches("(?s).*[\\r\\n<>].*") || email.matches("(?s).*[\\r\\n<> ].*"))
            throw new IOException("Enter a commit name and email address.");
        state.authorName = name.trim(); state.authorEmail = email.trim(); save();
    }
    private void publish() {
        projects = List.copyOf(state.projects); workspaces = List.copyOf(state.workspaces);
        tokenNotes = Map.copyOf(state.tokenNotes); tokenWarnings = Map.copyOf(state.tokenWarnings);
    }
    private void save() throws IOException { SynapMcConfig.write(config, state); publish(); syncUnifiedFolder(); }

    void syncUnifiedFolder() {
        if (SynapMcShared.config == null || SynapMcShared.roamingDir == null || SynapMcShared.core == null) return;
        try {
            if (!Files.isSameFile(roaming, SynapMcShared.roamingDir.toPath())) return;
            Map<String, String> folders = new LinkedHashMap<>(SynapMcShared.config.unifyFolders);
            if (!projects.isEmpty()) {
                Files.createDirectories(roaming.resolve("projects/github"));
                Files.createDirectories(roaming.resolve("projects/gitlab"));
                folders.putIfAbsent("git", "projects");
            } else folders.remove("git", "projects");
            if (!folders.equals(SynapMcShared.config.unifyFolders)
                    && !new com.synapmc.library.FSynapse(null, null).saveConfig("unifyFolders", folders))
                throw new IOException("Could not save the git unified folder.");
        } catch (IOException error) { SynapMcShared.error("[SynapMc] Could not link Git projects: " + error.getMessage()); }
    }

    public static String remote(String value) throws IOException {
        try {
            String input = value.trim();
            if (!input.contains("://")) input = "https://github.com/" + input;
            URI uri = URI.create(input);
            String host = uri.getHost();
            if (!"https".equals(uri.getScheme()) || uri.getRawUserInfo() != null || uri.getPort() != -1
                    || uri.getRawQuery() != null || uri.getRawFragment() != null || host == null) throw new IllegalArgumentException();
            host = host.toLowerCase(Locale.ROOT); GitCredentials.host(host);
            String name = uri.getRawPath();
            if (name.endsWith("/")) name = name.substring(0, name.length() - 1);
            if (name.endsWith(".git")) name = name.substring(0, name.length() - 4);
            String[] parts = name.substring(1).split("/", -1);
            if (parts.length < 2 || host.equals("github.com") && parts.length != 2) throw new IllegalArgumentException();
            for (String part : parts) if (!part.matches("[A-Za-z0-9][A-Za-z0-9_.-]*") || part.equals(".") || part.equals("..")) throw new IllegalArgumentException();
            return "https://" + host + name + ".git";
        } catch (RuntimeException | IOException error) {
            throw new IOException("Use owner/repository or an HTTPS GitHub/GitLab repository URL.");
        }
    }

    public synchronized Project add(String input, String customDirectory) throws IOException {
        String remote = remote(input);
        if (state.projects.stream().anyMatch(project -> project.remote.equalsIgnoreCase(remote)))
            throw new IOException("This repository is already linked. Select it to change its folder.");
        boolean managed = customDirectory.isBlank();
        String repo = remote.substring(remote.lastIndexOf('/') + 1, remote.length() - 4);
        String provider = URI.create(remote).getHost().equals("gitlab.com") ? "gitlab" : "github";
        Path folder = managed ? roaming.resolve("projects/" + provider).resolve(repo) : Path.of(customDirectory).toAbsolutePath().normalize();
        checkUnused(folder, null);
        prepare(remote, folder);
        Project project = new Project(remote, folder.toRealPath().toString(), managed);
        if (managed) {
            Files.createDirectories(folder.resolve(".ignore")); Files.createDirectories(folder.resolve(".private"));
            privateMetadata(project);
        }
        state.projects.add(project); save(); return project;
    }

    public synchronized Project changeDirectory(Project project, String directory) throws IOException {
        Path folder = Path.of(directory).toAbsolutePath().normalize();
        checkUnused(folder, project);
        prepare(project.remote, folder);
        Project replacement = new Project(project.remote, folder.toRealPath().toString(), false);
        int index = state.projects.indexOf(project);
        if (index < 0) throw new IOException("Reopen this repository.");
        state.projects.set(index, replacement); save(); return replacement;
    }
    private void checkUnused(Path folder, Project except) throws IOException {
        if (Files.exists(folder)) folder = folder.toRealPath();
        for (Project other : state.projects)
            if (!other.equals(except) && other.path().equals(folder)) throw new IOException("That folder is already linked to another repository.");
    }
    private void prepare(String remote, Path folder) throws IOException {
        Files.createDirectories(folder);
        if (Files.exists(folder.resolve(".git"))) {
            checkRepository(folder);
            String existing = git.run(folder, Map.of(), "", "config", "--get", "remote.origin.url").strip();
            if (!remote(existing).equalsIgnoreCase(remote)) throw new IOException("This folder has a different origin repository.");
            return;
        }
        // Refuse to accidentally initialize a folder inside another checkout.
        if (git.call(folder, Map.of(), "", "rev-parse", "--show-toplevel").code() == 0)
            throw new IOException("Choose a folder outside an existing checkout, or the checkout's root.");
        boolean empty;
        try (var files = Files.list(folder)) { empty = files.findAny().isEmpty(); }
        if (empty) {
            git.run(folder, Map.of(), credential(remote), "clone", "--filter=blob:none", "--no-checkout", "--no-tags", "--", remote, ".");
            sparseCheckout(folder, remote);
            if (!head(folder).isEmpty()) git.run(folder, Map.of(), credential(remote), "checkout", branch(folder));
            return;
        }
        // Link an existing folder without overwriting any of its files. The first review
        // shows how its contents differ from GitHub, including missing remote files.
        String refs = git.run(folder, Map.of(), credential(remote), "ls-remote", "--symref", remote, "HEAD");
        String branch = "main";
        for (String line : refs.split("\n")) if (line.startsWith("ref: refs/heads/")) branch = line.substring(16, line.indexOf('\t'));
        git.run(folder, Map.of(), "", "init", "--initial-branch=" + branch);
        git.run(folder, Map.of(), "", "remote", "add", "origin", remote);
        partialClone(folder);
        git.run(folder, Map.of(), credential(remote), "fetch", "--filter=blob:none", "--no-tags", "origin");
        GitHubGit.Result upstream = git.call(folder, Map.of(), "", "rev-parse", "--verify", "refs/remotes/origin/" + branch);
        if (upstream.code() == 0) {
            git.run(folder, Map.of(), "", "update-ref", "refs/heads/" + branch, upstream.output().strip());
            git.run(folder, Map.of(), "", "read-tree", upstream.output().strip());
        }
        git.run(folder, Map.of(), "", "config", "branch." + branch + ".remote", "origin");
        git.run(folder, Map.of(), "", "config", "branch." + branch + ".merge", "refs/heads/" + branch);
    }

    static Set<String> scriptExtensions() {
        Set<String> extensions = new TreeSet<>(List.of("js", "lua", "py", "rb", "ts", "kts", "groovy", "r"));
        if (SynapMcShared.core != null) {
            Map<String, String> installed = SynapMcShared.core.getAvailableLanguages();
            if (installed != null && !installed.isEmpty()) {
                extensions.clear();
                for (String extension : installed.values()) {
                    if (extension == null) continue;
                    String normalized = extension.replaceFirst("^\\.", "").toLowerCase(Locale.ROOT);
                    if (normalized.matches("[a-z0-9]+")) extensions.add(normalized);
                }
            }
        }
        return extensions;
    }
    private static Set<String> downloadExtensions() {
        Set<String> extensions = scriptExtensions();
        extensions.addAll(List.of("jar", "txt"));
        return extensions;
    }
    private static boolean downloaded(String path, Set<String> extensions) {
        String filename = path.substring(path.lastIndexOf('/') + 1);
        // Git control files preserve ignore rules and extension/LFS attributes.
        if (filename.equals(".gitignore") || filename.equals(".gitattributes") || path.equals("manifest.json")) return true;
        int dot = filename.lastIndexOf('.');
        return dot >= 0 && extensions.contains(filename.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
    private void partialClone(Path folder) throws IOException {
        git.run(folder, Map.of(), "", "config", "remote.origin.promisor", "true");
        git.run(folder, Map.of(), "", "config", "remote.origin.partialclonefilter", "blob:none");
    }
    private void sparseCheckout(Path folder, String remote) throws IOException {
        partialClone(folder);
        List<String> args = new ArrayList<>(List.of("sparse-checkout", "set", "--no-cone", "--no-sparse-index", "--"));
        args.addAll(List.of(".gitignore", ".gitattributes", "/manifest.json"));
        for (String extension : downloadExtensions()) {
            StringBuilder glob = new StringBuilder("*.");
            for (char letter : extension.toCharArray()) {
                if (Character.isLetter(letter)) glob.append('[').append(letter).append(Character.toUpperCase(letter)).append(']');
                else glob.append(letter);
            }
            args.add(glob.toString());
        }
        git.run(folder, Map.of(), credential(remote), args.toArray(String[]::new));
    }
    public synchronized List<RefreshResult> refreshAll() {
        List<RefreshResult> results = new ArrayList<>();
        for (Project project : projects()) {
            try { results.add(new RefreshResult(project, review(project, true), "")); }
            catch (IOException | RuntimeException error) { results.add(new RefreshResult(project, null, error.getMessage() == null ? "Could not refresh repository." : error.getMessage())); }
        }
        return List.copyOf(results);
    }

    private Path manifestPath(Project project) throws IOException {
        checkOrigin(project);
        Path path = project.path().resolve("manifest.json");
        // Earlier filtered checkouts omitted JSON. Restore only a skipped manifest;
        // an ordinary local deletion must remain a deletion.
        if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)
                && git.run(project.path(), Map.of(), "", "ls-files", "-t", "--", "manifest.json").startsWith("S "))
            git.run(project.path(), Map.of(), credential(project.remote), "sparse-checkout", "add", "--", "/manifest.json");
        return path;
    }
    public synchronized boolean hasManifest(Project project) throws IOException {
        return Files.exists(manifestPath(project), LinkOption.NOFOLLOW_LINKS);
    }
    public synchronized GitProjectManifest.Update updateManifest(Project project) throws IOException {
        if (!hasToken(project.host())) throw new IOException("Add a token for " + project.provider() + " in Profile before editing the manifest.");
        Path file = manifestPath(project);
        String paths = git.run(project.path(), Map.of(), "", "ls-files", "--cached", "--others", "--exclude-standard", "-z");
        SortedSet<String> scripts = new TreeSet<>();
        Set<String> omitted = new HashSet<>();
        for (String entry : git.run(project.path(), Map.of(), "", "ls-files", "-t", "-z").split("\u0000"))
            if (entry.startsWith("S ") && Files.notExists(project.path().resolve(entry.substring(2)), LinkOption.NOFOLLOW_LINKS))
                omitted.add(entry.substring(2));
        Set<String> extensions = scriptExtensions();
        for (String path : paths.split("\u0000")) {
            int dot = path.lastIndexOf('.');
            if (dot < 0 || !extensions.contains(path.substring(dot + 1).toLowerCase(Locale.ROOT)) || excluded(path, project.path())) continue;
            if (GitProjectManifest.localFile(project.path(), path)) scripts.add(path);
        }
        return GitProjectManifest.update(file, project, branch(project.path()), state.authorName, scripts, omitted);
    }
    private void checkRepository(Path folder) throws IOException {
        String top = git.run(folder, Map.of(), "", "rev-parse", "--show-toplevel").strip();
        if (!Path.of(top).toRealPath().equals(folder.toRealPath())) throw new IOException("Choose the repository's root directory.");
    }
    private void checkOrigin(Project project) throws IOException {
        checkRepository(project.path());
        String origin = git.run(project.path(), Map.of(), "", "config", "--get-all", "remote.origin.url").strip();
        GitHubGit.Result pushUrl = git.call(project.path(), Map.of(), "", "config", "--get-all", "remote.origin.pushurl");
        String push = pushUrl.code() == 0 ? pushUrl.output().strip() : origin;
        if (!remote(origin).equalsIgnoreCase(project.remote) || !remote(push).equalsIgnoreCase(project.remote))
            throw new IOException("Origin changed. Relink this repository before pulling or pushing.");
        privateMetadata(project);
    }

    private void privateMetadata(Project project) throws IOException {
        Path folder = project.path(), source = folder.resolve(".git");
        // Leave custom checkouts, worktree pointers, and already migrated repositories alone.
        if (!project.managed || !Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) return;
        Path privacy = folder.resolve(".private"), destination = privacy.resolve(".git");
        if (Files.isSymbolicLink(privacy) || Files.exists(destination, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Cannot move Git metadata: .private/ must be a local folder with no existing .git entry.");
        for (String lock : List.of("index.lock", "config.lock", "HEAD.lock", "packed-refs.lock", "shallow.lock"))
            if (Files.exists(source.resolve(lock))) throw new IOException("Git is busy in this checkout. Try again when it finishes.");
        Files.createDirectories(privacy);
        // Git moves metadata and creates its root pointer itself, preserving history,
        // refs, index, and ordinary command-line/GUI repository discovery.
        git.run(folder, Map.of(), "", "init", "--quiet", "--separate-git-dir=" + destination);
    }

    public synchronized void addWorkspace(String name, String directory) throws IOException {
        if (!name.matches("[A-Za-z0-9][A-Za-z0-9_.-]*") || name.equals(".") || name.equals(".."))
            throw new IOException("Use letters, numbers, dots, underscores, or hyphens for the workspace name.");
        Path folder = Path.of(directory).toRealPath();
        if (!Files.isDirectory(folder)) throw new IOException("Choose an existing workspace folder.");
        for (String provider : List.of("github", "gitlab")) {
            Path managed = roaming.resolve("projects/" + provider);
            if (folder.startsWith(Files.exists(managed) ? managed.toRealPath() : managed))
                throw new IOException("Workspaces link folders outside projects/" + provider + ".");
        }
        for (Workspace workspace : state.workspaces) if (workspace.name.equals(name)) throw new IOException("That workspace name is already used.");
        state.workspaces.add(new Workspace(name, folder.toString())); save();
    }
    public synchronized void linkWorkspace(Project project, Workspace workspace) throws IOException {
        if (!state.workspaces.contains(workspace)) throw new IOException("Reopen the workspace list.");
        Path ignore = project.path().resolve(".ignore");
        Files.createDirectories(ignore);
        Path link = ignore.resolve(workspace.name);
        if (Files.exists(link, LinkOption.NOFOLLOW_LINKS)) throw new IOException("That workspace link already exists.");
        try { Files.createSymbolicLink(link, Path.of(workspace.directory)); }
        catch (IOException | UnsupportedOperationException error) { throw new IOException("Could not create workspace link. On Windows, enable Developer Mode or allow symbolic links."); }
    }
    public synchronized void remove(Project project) throws IOException { state.projects.remove(project); save(); }
    public synchronized void removeWorkspace(Workspace workspace) throws IOException { state.workspaces.remove(workspace); save(); }

    private boolean excluded(String path, Path folder) {
        Path credentialFile = credentials.path();
        if (credentialFile.startsWith(folder) && path.equals(folder.relativize(credentialFile).toString().replace('\\', '/'))) return true;
        return path.equals(".ignore") || path.startsWith(".ignore/") || path.equals(".private") || path.startsWith(".private/");
    }
    private record Index(Path file, Map<String, String> env) implements AutoCloseable {
        @Override public void close() throws IOException { Files.deleteIfExists(file); Files.deleteIfExists(Path.of(file + ".lock")); }
    }
    private Index index(Path folder, String head, String remote) throws IOException {
        Path file = Files.createTempFile("synapmc-git-index-", ".tmp"); Files.delete(file);
        Index index = new Index(file, Map.of("GIT_INDEX_FILE", file.toString()));
        try {
            if (head.isEmpty()) git.run(folder, index.env, credential(remote), "read-tree", "--empty");
            else git.run(folder, index.env, credential(remote), "read-tree", head);
            // read-tree loses sparse index flags. Missing files outside our download
            // selection must stay in the tree rather than turn into remote deletions.
            String files = git.run(folder, index.env, "", "ls-files", "-z");
            Set<String> sparseMissing = new HashSet<>();
            for (String entry : git.run(folder, Map.of(), "", "ls-files", "-t", "-z").split("\u0000"))
                if (entry.startsWith("S ")) sparseMissing.add(entry.substring(2));
            StringBuilder skipped = new StringBuilder();
            Set<String> extensions = downloadExtensions();
            for (String path : files.split("\u0000")) if (!path.isEmpty() && (!downloaded(path, extensions) || sparseMissing.contains(path))
                    && Files.notExists(folder.resolve(path), LinkOption.NOFOLLOW_LINKS)) skipped.append(path).append('\0');
            if (!skipped.isEmpty()) git.runInput(folder, index.env, new GitHubGit.Credential("github.com", ""),
                    skipped.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), "update-index", "--skip-worktree", "-z", "--stdin");
            List<String> args = new ArrayList<>(List.of("add", "--all", "--sparse", "--", ".", ":(top,exclude).ignore", ":(top,exclude).ignore/**",
                    ":(top,exclude).private", ":(top,exclude).private/**"));
            Path credentialFile = credentials.path();
            if (credentialFile.startsWith(folder)) args.add(":(top,literal,exclude)" + folder.relativize(credentialFile).toString().replace('\\', '/'));
            git.run(folder, index.env, credential(remote), args.toArray(String[]::new));
            return index;
        } catch (IOException error) { index.close(); throw error; }
    }
    private String head(Path folder) throws IOException {
        GitHubGit.Result result = git.call(folder, Map.of(), "", "rev-parse", "--verify", "HEAD");
        return result.code() == 0 ? result.output().strip() : "";
    }
    private String emptyTree(Path folder) throws IOException { return git.run(folder, Map.of(), "", "hash-object", "-w", "-t", "tree", "--stdin").strip(); }
    private String branch(Path folder) throws IOException {
        GitHubGit.Result result = git.call(folder, Map.of(), "", "symbolic-ref", "--quiet", "--short", "HEAD");
        if (result.code() != 0) throw new IOException("Checkout a branch before using Pull or Push.");
        return result.output().strip();
    }
    private String base(Path folder, String branch) throws IOException {
        GitHubGit.Result result = git.call(folder, Map.of(), "", "rev-parse", "--verify", "refs/remotes/origin/" + branch);
        return result.code() == 0 ? result.output().strip() : emptyTree(folder);
    }
    public synchronized Review review(Project project, boolean fetch) throws IOException {
        checkOrigin(project);
        Path folder = project.path(); String branch = branch(folder);
        if (fetch) {
            partialClone(folder);
            git.run(folder, Map.of(), credential(project.remote), "fetch", "--filter=blob:none", "--no-tags", "origin");
        }
        String head = head(folder), base = base(folder, branch);
        int ahead = 0;
        if (!head.isEmpty()) {
            boolean hasRemote = !base.equals(emptyTree(folder));
            if (hasRemote && git.call(folder, Map.of(), "", "merge-base", "--is-ancestor", base, head).code() != 0)
                throw new IOException(project.provider() + " has newer or divergent commits. Pull before reviewing a push.");
            String range = hasRemote ? base + ".." + head : head;
            String outgoing = git.run(folder, Map.of(), "", "log", "-m", "--format=", "--name-only", "-z", range);
            for (String path : outgoing.split("\u0000")) if (excluded(path.stripLeading(), folder))
                throw new IOException("An unpublished commit contains excluded files (.ignore/, .private/, or saved credentials). Correct those commits using Git before pushing.");
            ahead = Integer.parseInt(git.run(folder, Map.of(), "", "rev-list", "--count", range).strip());
        }
        try (Index index = index(folder, head, project.remote)) {
            String tree = git.run(folder, index.env, "", "write-tree", "--missing-ok").strip();
            String names = git.run(folder, Map.of(), "", "diff", "--no-ext-diff", "--no-textconv", "--no-renames", "--name-status", "-z", base, tree, "--");
            List<Change> changes = new ArrayList<>();
            String[] pieces = names.split("\u0000");
            for (int i = 0; i + 1 < pieces.length; i += 2) changes.add(new Change(pieces[i], pieces[i + 1]));
            return new Review(project, branch, head, base, tree, ahead, List.copyOf(changes));
        }
    }
    public synchronized String diff(Review review, Change change) throws IOException {
        if (!review.changes.contains(change)) throw new IOException("Select a file from the review.");
        String diff = git.run(review.project.path(), Map.of(), credential(review.project.remote), "diff", "--no-ext-diff", "--no-textconv", "--no-color", "--no-renames",
                "--unified=3", review.base, review.tree, "--", change.path);
        return diff.length() <= 128_000 ? diff : diff.substring(0, 128_000) + "\n[Diff preview truncated]";
    }
    public synchronized void revert(Review approved, Change change) throws IOException {
        if (!approved.changes.contains(change)) throw new IOException("Select a file from the review.");
        Path folder = approved.project.path().toAbsolutePath().normalize();
        Path relative = Path.of(change.path), target = folder.resolve(relative).normalize();
        if (relative.isAbsolute() || relative.getNameCount() == 0 || !target.startsWith(folder) || target.equals(folder)
                || !folder.relativize(target).equals(relative) || excluded(change.path, folder))
            throw new IOException("Select a repository file to revert.");
        Path part = folder;
        for (Path name : relative) {
            part = part.resolve(name);
            if (name.toString().equalsIgnoreCase(".git") || Files.isSymbolicLink(part))
                throw new IOException("Cannot revert Git metadata or a symbolic link from the game.");
        }
        if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Cannot replace a folder when reverting a file.");
        Map<String, String> literal = Map.of("GIT_LITERAL_PATHSPECS", "1");
        Review current = review(approved.project, false);
        if (!current.branch.equals(approved.branch) || !current.head.equals(approved.head) || !current.base.equals(approved.base)
                || !current.changes.contains(change) || !treeFile(folder, approved.tree, change.path).equals(treeFile(folder, current.tree, change.path)))
            throw new IOException("This file or its commits changed since review. Refresh and review again.");
        if (git.call(folder, literal, "", "diff", "--cached", "--quiet", "--", change.path).code() != 0)
            throw new IOException("This file has staged changes. Commit or unstage it in Git before reverting from the game.");
        String original = treeFile(folder, approved.base, change.path);
        if (original.isEmpty()) Files.deleteIfExists(target);
        else {
            if (!original.startsWith("100644 ") && !original.startsWith("100755 "))
                throw new IOException("Cannot revert a symbolic link or submodule from the game.");
            git.run(folder, literal, credential(approved.project.remote), "restore", "--source=" + approved.base, "--worktree", "--", change.path);
        }
    }
    private String treeFile(Path folder, String tree, String path) throws IOException {
        return git.run(folder, Map.of("GIT_LITERAL_PATHSPECS", "1"), "", "ls-tree", "--full-tree", "-z", tree, "--", path);
    }
    public synchronized void pull(Project project) throws IOException {
        checkOrigin(project);
        Path folder = project.path(); String branch = branch(folder);
        sparseCheckout(folder, project.remote);
        // Fast-forward only: Git preserves local files and refuses conflicting updates.
        git.run(folder, Map.of(), credential(project.remote), "fetch", "--filter=blob:none", "--no-tags", "origin", branch);
        // Diff statistics would lazily download omitted blobs just to count lines.
        git.run(folder, Map.of(), credential(project.remote), "merge", "--ff-only", "--no-edit", "--no-stat", "FETCH_HEAD");
    }
    public synchronized void push(Review approved, String message) throws IOException {
        if (message.isBlank()) throw new IOException("Enter a commit message.");
        Review current = review(approved.project, false);
        if (!current.equals(approved)) throw new IOException("Files or commits changed since review. Refresh and review again.");
        Path folder = approved.project.path();
        if (git.call(folder, Map.of(), "", "diff", "--cached", "--quiet").code() != 0)
            throw new IOException("This checkout has staged changes. Commit or unstage them in Git before pushing from the game.");
        String headTree = approved.head.isEmpty() ? emptyTree(folder) : git.run(folder, Map.of(), "", "rev-parse", approved.head + "^{tree}").strip();
        if (!approved.tree.equals(headTree)) {
            Path gitIndex = Path.of(git.run(folder, Map.of(), "", "rev-parse", "--git-path", "index").strip());
            if (!gitIndex.isAbsolute()) gitIndex = folder.resolve(gitIndex);
            Path lock = Path.of(gitIndex + ".lock");
            try { Files.createFile(lock); }
            catch (FileAlreadyExistsException error) { throw new IOException("Git is busy in this checkout. Try again when it finishes."); }
            try (Index index = index(folder, approved.head, approved.project.remote)) {
                String tree = git.run(folder, index.env, "", "write-tree", "--missing-ok").strip();
                if (!tree.equals(approved.tree) || !head(folder).equals(approved.head) || !branch(folder).equals(approved.branch))
                    throw new IOException("Files changed since review. Refresh and review again.");
                if (git.call(folder, Map.of(), "", "diff", "--cached", "--quiet").code() != 0)
                    throw new IOException("Staged changes appeared since review. Commit or unstage them before pushing.");
                Map<String, String> identity = new LinkedHashMap<>();
                if (state.authorName != null && !state.authorName.isBlank()) {
                    identity.put("GIT_AUTHOR_NAME", state.authorName); identity.put("GIT_COMMITTER_NAME", state.authorName);
                    identity.put("GIT_AUTHOR_EMAIL", state.authorEmail); identity.put("GIT_COMMITTER_EMAIL", state.authorEmail);
                }
                List<String> args = new ArrayList<>(List.of("commit-tree", tree));
                if (!approved.head.isEmpty()) args.addAll(List.of("-p", approved.head));
                args.addAll(List.of("-m", message));
                String commit = git.run(folder, identity, "", args.toArray(String[]::new)).strip();
                String previous = approved.head.isEmpty() ? "0".repeat(commit.length()) : approved.head;
                Files.copy(index.file, lock, StandardCopyOption.REPLACE_EXISTING);
                git.run(folder, Map.of(), "", "update-ref", "-m", "SynapMc GitHub push", "refs/heads/" + approved.branch, commit, previous);
                try { Files.move(lock, gitIndex, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException error) { Files.move(lock, gitIndex, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(lock); }
        }
        try { git.run(folder, Map.of(), credential(approved.project.remote), "push", "--set-upstream", "origin", "refs/heads/" + approved.branch + ":refs/heads/" + approved.branch); }
        catch (IOException error) { throw new IOException("Push failed; your local commit is kept. Refresh to retry. " + error.getMessage()); }
    }
}
