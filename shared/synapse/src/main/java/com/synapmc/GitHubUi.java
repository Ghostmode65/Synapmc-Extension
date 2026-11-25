package com.synapmc;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/** Native Git project page. Rendering never reads repositories or contacts remote hosts. */
public final class GitHubUi {
    private final Object screen;
    private final List<Object> widgets = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private final Queue<Runnable> completions = new ConcurrentLinkedQueue<>();
    private GitHubProjects store;
    private GitHubProjects.Project project;
    private GitHubProjects.Review review;
    private GitHubProjects.Review diffReview;
    private GitHubProjects.Change diffChange;
    private List<GitHubProjects.Project> cachedProjects = List.of();
    private List<GitHubProjects.Workspace> cachedWorkspaces = List.of();
    private Map<GitHubProjects.Project, GitHubProjects.RefreshResult> refreshed = Map.of();
    private String view = "projects", message = "", diff = "", diffName = "";
    private String profileHost = "github.com", returnView = "projects", scrollbarLayout = "";
    private String providerFilter = "All";
    private boolean changesOnly;
    private Boolean gitAvailable;
    private int width, height, left, panelWidth, offset, visibleRows, totalRows, listTop, listWidth, returnOffset;
    private Object scrollbar;
    private boolean updatingScrollbar;
    private int requestedOffset = -1;
    private boolean busy;
    private Runnable pending;
    private record Label(String text, int y, int color) { }
    private record ManifestReview(GitProjectManifest.Update update, GitHubProjects.Review review) { }
    @FunctionalInterface private interface Work<T> { T run() throws Exception; }

    public GitHubUi(Object screen) { this.screen = screen; }

    public void init() {
        gitAvailable = GitInstallation.available();
        for (Object widget : widgets) if (widget != scrollbar) MacroScreenBridge.removeWidget(screen, widget);
        widgets.clear(); labels.clear();
        width = MacroScreenBridge.dimension(screen, "width", "field_22789");
        height = MacroScreenBridge.dimension(screen, "height", "field_22790");
        panelWidth = Math.min(640, width - 24); left = view.equals("profile") ? 12 : (width - panelWidth) / 2;
        listWidth = panelWidth - 14; totalRows = 0; listTop = 0;
        try {
            if (store == null) store = GitHubProjects.open();
            if (!busy) {
                cachedProjects = store.projects(); cachedWorkspaces = store.workspaces();
            }
            switch (view) {
                case "project" -> repository();
                case "workspaces" -> workspaces(false);
                case "workspace" -> workspaces(true);
                case "diff" -> diff();
                case "profile" -> profile();
                default -> projects();
            }
            syncScrollbar();
            button(left, height - 28, 64, "Back", this::back);
            if (!view.equals("profile")) {
                boolean selected = project != null && !view.equals("projects") && !view.equals("workspaces");
                Object sync = button(left + panelWidth - 98, height - 28, 22, "↻", selected ? this::refresh : this::syncAll);
                MacroScreenBridge.refreshIcon(sync);
                MacroScreenBridge.setTooltip(sync, selected ? "Refresh this repository" : "Refresh all repositories");
                button(left + panelWidth - 72, height - 28, 72, "Profile", this::openProfile);
            }
        } catch (Exception error) { fail(error); }
    }
    private Object button(int x, int y, int w, String caption, Runnable action) throws Exception {
        Object widget = MacroScreenBridge.addButton(screen, x, y, w, 18, fit(caption, w - 6), () -> { if (!busy) action.run(); });
        MacroScreenBridge.setTooltip(widget, caption.equals("↻") ? "Refresh this repository" : caption);
        MacroScreenBridge.active(widget, !busy); widgets.add(widget); return widget;
    }
    private void row(int y, String[] captions, Runnable[] actions) throws Exception {
        int slot = panelWidth / captions.length;
        for (int i = 0; i < captions.length; i++) button(left + i * slot, y, slot - 4, captions[i], actions[i]);
    }
    private void viewport(int size, int top, int rowHeight) {
        listTop = top; totalRows = size;
        visibleRows = Math.max(1, (height - 64 - top) / rowHeight);
        offset = Math.max(0, Math.min(offset, Math.max(0, totalRows - visibleRows)));
    }
    private <T> List<T> visible(List<T> entries) { return entries.subList(offset, Math.min(entries.size(), offset + visibleRows)); }
    private void syncScrollbar() throws Exception {
        if (totalRows <= visibleRows || listTop == 0) {
            if (scrollbar != null) MacroScreenBridge.removeWidget(screen, scrollbar);
            scrollbar = null; scrollbarLayout = ""; return;
        }
        String layout = view + ":" + width + ":" + height + ":" + listTop + ":" + totalRows + ":" + visibleRows;
        if (scrollbar == null || !layout.equals(scrollbarLayout)) {
            if (scrollbar != null) MacroScreenBridge.removeWidget(screen, scrollbar);
            scrollbar = MacroScreenBridge.addScrollbar(screen, left + panelWidth - 10, listTop, 8,
                    height - 64 - listTop, (double) totalRows / visibleRows, value -> {
                        if (!updatingScrollbar && !busy) requestedOffset = (int) Math.round(value * visibleRows);
                    });
            scrollbarLayout = layout;
        }
        updatingScrollbar = true;
        try { MacroScreenBridge.scrollbarPercent(scrollbar, (double) offset / (totalRows - visibleRows)); }
        finally { updatingScrollbar = false; }
        MacroScreenBridge.active(scrollbar, !busy); widgets.add(scrollbar);
    }
    private void projects() throws Exception {
        int cloneWidth = panelWidth / 3 - 4;
        button(left, 42, cloneWidth, "Clone Repo", this::addRepo);
        int available = panelWidth - cloneWidth - 8;
        int providerWidth = Math.min(64, available / 2);
        Object provider = button(left + cloneWidth + 4, 42, providerWidth, providerFilter, this::cycleProvider);
        MacroScreenBridge.setTooltip(provider, "Provider: " + providerFilter + "\nClick to cycle All, GitHub, GitLab");
        int changesWidth = Math.min(88, available - providerWidth);
        Object changes = button(left + cloneWidth + providerWidth + 8, 42, changesWidth,
                changesOnly ? "With changes" : "All repos", this::toggleChanges);
        MacroScreenBridge.setTooltip(changes, changesOnly
                ? "Show repositories with changes or unpublished commits; unchecked and failed reviews stay visible."
                : "Show all repositories. Click to show those with changes.");
        if (Boolean.FALSE.equals(gitAvailable)) {
            int downloadX = left + cloneWidth + providerWidth + changesWidth + 12;
            int downloadWidth = Math.min(92, left + panelWidth - downloadX);
            if (downloadWidth > 0) {
                Object download = button(downloadX, 42, downloadWidth, "Download Git", () -> {
                    try { MinecraftAccess.openUrl(GitInstallation.DOWNLOAD_URL); }
                    catch (Exception error) { fail(error); }
                });
                MacroScreenBridge.setTooltip(download, "Download Git\nGit is unavailable on PATH. Open the official download page.\nAfter installing Git, restart your launcher and Minecraft.");
            }
        }
        List<GitHubProjects.Project> projects = cachedProjects.stream()
                .filter(entry -> providerFilter.equals("All") || entry.provider().equals(providerFilter))
                .filter(entry -> !changesOnly || hasChanges(entry)).toList();
        viewport(projects.size(), 72, 22);
        int y = 72;
        for (GitHubProjects.Project entry : visible(projects)) {
            GitHubProjects.RefreshResult result = refreshed.get(entry);
            String status = result == null ? "" : result.review() == null ? " · Check repository" : " · " + result.review().changes().size() + " changes";
            Object select = button(left, y, listWidth - 28, entry.provider() + " · " + entry.name() + status, () -> select(entry));
            MacroScreenBridge.setTooltip(select, entry.name() + "\n" + entry.directory() + (result != null && !result.error().isEmpty() ? "\n" + result.error() : ""));
            button(left + listWidth - 24, y, 24, "X", () -> confirm("Remove project link? Files will be kept.", () ->
                    run("Removing link...", () -> { store.remove(entry); return null; }, ignored -> init())));
            y += 22;
        }
    }
    private boolean hasChanges(GitHubProjects.Project entry) {
        GitHubProjects.RefreshResult result = refreshed.get(entry);
        return result == null || result.review() == null || !result.review().changes().isEmpty() || result.review().ahead() > 0;
    }
    private void cycleProvider() {
        providerFilter = switch (providerFilter) { case "All" -> "GitHub"; case "GitHub" -> "GitLab"; default -> "All"; };
        offset = 0; requestedOffset = -1; init();
    }
    private void toggleChanges() {
        changesOnly = !changesOnly;
        offset = 0; requestedOffset = -1;
        if (changesOnly && cachedProjects.stream().anyMatch(entry -> !refreshed.containsKey(entry))) syncAll();
        else init();
    }
    private void select(GitHubProjects.Project selected) {
        project = selected;
        GitHubProjects.RefreshResult result = refreshed.get(selected);
        review = result == null ? null : result.review();
        navigate("project");
        if (result == null) refresh();
        else if (!result.error().isEmpty()) message = result.error();
    }
    private void repository() throws Exception {
        labels.add(new Label(project.name(), 42, 0xFFFFFFFF));
        labels.add(new Label(project.directory(), 57, 0xFFBBBBBB));
        boolean token = store.hasToken(project.host());
        String[] captions = {"Pull", token ? "Push" : "Token required", "Workspace"};
        Runnable[] actions = {this::pull, token ? this::push : () -> { }, () -> navigate("workspace")};
        int slot = (panelWidth - (token ? 26 : 0)) / captions.length;
        int actionWidth = (slot - 4) / 2;
        int actionX = left;
        for (int i = 0; i < captions.length; i++) {
            int buttonWidth = !token && i == 1 ? Math.max(actionWidth, MacroScreenBridge.textWidth(screen, captions[i]) + 8) : actionWidth;
            Object action = button(actionX, 75, buttonWidth, captions[i], actions[i]);
            actionX += buttonWidth + 4;
            if (!token && i == 1) {
                MacroScreenBridge.active(action, false);
                MacroScreenBridge.textColor(action, 0xFFFF5555);
                MacroScreenBridge.setTooltip(action, "Token required\nAdd a " + project.provider() + " token in Profile to push.");
            }
        }
        if (token) {
            Object manifest = button(actionX, 75, 22, "🗎", this::confirmManifest);
            MacroScreenBridge.setTooltip(manifest, "Manifest");
        }
        if (review == null) {
            labels.add(new Label("Refresh to review additions, changes, and deletions.", 104, 0xFFBBBBBB));
            viewport(0, 122, 20); return;
        }
        labels.add(new Label(review.branch() + " · " + review.changes().size() + " file changes · " + review.ahead() + " unpublished commits", 104, 0xFFBBBBBB));
        List<GitHubProjects.Change> changes = review.changes(); viewport(changes.size(), 122, 20);
        int y = 122;
        for (GitHubProjects.Change change : visible(changes)) {
            String prefix = change.kind().equals("A") ? "+ New: " : change.kind().equals("D") ? "- Delete: " : "~ Changed: ";
            Object control = button(left, y, listWidth, prefix + change.path(), () -> openDiff(change));
            MacroScreenBridge.buttonColor(control, change.kind().equals("A") ? 0x88428A50 : change.kind().equals("D") ? 0x889A4040 : 0x88897838);
            MacroScreenBridge.setTooltip(control, prefix + change.path() + "\nClick to view the diff"); y += 20;
        }
    }
    private void confirmManifest() {
        run("Checking manifest...", () -> store.hasManifest(project), present ->
                confirm("Do you want to " + (present ? "update" : "add") + " manifest.json?", this::updateManifest));
    }
    private void updateManifest() {
        invalidateReview();
        run("Updating manifest...", () -> {
            GitProjectManifest.Update update = store.updateManifest(project);
            return new ManifestReview(update, store.review(project, false));
        }, result -> {
            review = result.review(); remember(review);
            message = "Manifest " + (result.update().created() ? "added" : "updated") + ": " + result.update().added()
                    + " scripts added, " + result.update().removed() + " removed. Review and Push to publish.";
            init();
        });
    }
    private void refresh() {
        invalidateReview();
        run("Checking repository...", () -> store.review(project, true), result -> { review = result; remember(result); init(); });
    }
    private void invalidateReview() {
        review = null;
        Map<GitHubProjects.Project, GitHubProjects.RefreshResult> updated = new LinkedHashMap<>(refreshed);
        updated.remove(project); refreshed = Map.copyOf(updated);
    }
    private void remember(GitHubProjects.Review value) {
        Map<GitHubProjects.Project, GitHubProjects.RefreshResult> updated = new LinkedHashMap<>(refreshed);
        updated.put(value.project(), new GitHubProjects.RefreshResult(value.project(), value, "")); refreshed = Map.copyOf(updated);
    }
    private void syncAll() {
        run("Refreshing all repositories...", store::refreshAll, results -> {
            Map<GitHubProjects.Project, GitHubProjects.RefreshResult> updated = new LinkedHashMap<>();
            int completed = 0;
            for (GitHubProjects.RefreshResult result : results) {
                updated.put(result.project(), result);
                if (result.review() != null) completed++;
            }
            refreshed = Map.copyOf(updated);
            if (project != null && updated.containsKey(project)) review = updated.get(project).review();
            message = "Refreshed " + completed + " / " + results.size() + " repositories.";
            init();
        });
    }
    private void pull() {
        invalidateReview();
        run("Pulling from " + project.provider() + "...", () -> { store.pull(project); return store.review(project, false); },
                result -> { review = result; remember(result); message = "Pull complete."; init(); });
    }
    private void push() {
        if (!store.hasToken(project.host())) { message = "Add a token for " + project.provider() + " in Profile before pushing."; return; }
        if (review == null) { message = "Refresh and review the changes before pushing."; return; }
        if (review.changes().isEmpty() && review.ahead() == 0) { message = "Already up to date."; return; }
        GitHubProjects.Review approved = review;
        prompt("Commit message (.ignore/ and .private/ excluded)", "Update scripts", value -> confirm("Push " + approved.changes().size() + " file changes and " + approved.ahead() +
                " unpublished commits to " + project.name() + " (" + approved.branch() + ")?", () -> {
                    invalidateReview();
                    run("Pushing to " + project.provider() + "...", () -> { store.push(approved, value); return store.review(project, true); },
                            result -> { review = result; remember(result); message = "Push complete."; init(); });
                }));
    }
    private void openDiff(GitHubProjects.Change change) {
        GitHubProjects.Review selected = review;
        run("Loading diff...", () -> store.diff(selected, change), result -> {
            diff = result; diffName = change.path(); diffReview = selected; diffChange = change; navigate("diff");
        });
    }
    private void cancelDiff() {
        GitHubProjects.Review approved = diffReview;
        GitHubProjects.Change change = diffChange;
        if (approved == null || change == null) return;
        confirm("Do you want to revert changes to " + change.path() + "?", () -> {
            invalidateReview();
            run("Reverting changes...", () -> {
                store.revert(approved, change); return store.review(approved.project(), false);
            }, result -> {
                review = result; remember(result); diffReview = null; diffChange = null;
                navigate("project"); message = "Changes reverted: " + change.path();
            });
        });
    }
    private void diff() throws Exception {
        labels.add(new Label(diffName, 42, 0xFFFFFFFF));
        button(left + panelWidth - 76, 54, 76, "Copy", () -> MinecraftAccess.copyToClipboard(diff));
        button(left + panelWidth - 76, 76, 76, "Cancel", this::cancelDiff);
        List<String> lines = new ArrayList<>();
        for (String source : diff.split("\n", -1)) {
            String remaining = source;
            while (MacroScreenBridge.textWidth(screen, remaining) > listWidth) {
                int end = Math.min(remaining.length(), Math.max(1, listWidth / 6));
                while (end > 1 && MacroScreenBridge.textWidth(screen, remaining.substring(0, end)) > listWidth) end--;
                lines.add(remaining.substring(0, end)); remaining = source.substring(0, Math.min(1, source.length())) + " " + remaining.substring(end);
            }
            lines.add(remaining);
        }
        viewport(lines.size(), 102, 11);
        int y = 102;
        for (String line : visible(lines)) {
            int color = line.startsWith("+") ? 0xFF88DD99 : line.startsWith("-") ? 0xFFFF8888 : line.startsWith("@@") ? 0xFF88BBFF : 0xFFDDDDDD;
            labels.add(new Label(line, y, color)); y += 11;
        }
    }
    private void workspaces(boolean linking) throws Exception {
        labels.add(new Label(linking ? project.name() + "/.ignore/" : "Workspaces", 42, 0xFFFFFFFF));
        if (linking) row(57, new String[] {"Add Workspace", "Change Directory"}, new Runnable[] {this::addWorkspace, this::changeFolder});
        else button(left, 57, 100, "Add Workspace", this::addWorkspace);
        List<GitHubProjects.Workspace> workspaces = cachedWorkspaces; viewport(workspaces.size(), 84, 22);
        if (workspaces.isEmpty()) labels.add(new Label("Add a workspace folder to link it into a repository.", 87, 0xFFBBBBBB));
        int y = 84;
        for (GitHubProjects.Workspace workspace : visible(workspaces)) {
            Object select = button(left, y, listWidth - 28, workspace.name() + " → " + workspace.directory(), () -> {
                if (linking) run("Linking workspace...", () -> { store.linkWorkspace(project, workspace); return null; },
                        ignored -> { message = "Workspace linked in .ignore/."; init(); });
                else message = "Select a repository, then Workspace, to link this workspace.";
            });
            MacroScreenBridge.setTooltip(select, workspace.name() + "\n" + workspace.directory());
            button(left + listWidth - 24, y, 24, "X", () -> confirm("Remove workspace from the list? Existing links and files will be kept.", () ->
                    run("Removing workspace...", () -> { store.removeWorkspace(workspace); return null; }, ignored -> init())));
            y += 22;
        }
    }
    private void addRepo() {
        prompt("GitHub/GitLab URL or GitHub owner/repository", "", remote -> {
            try {
                String url = GitHubProjects.remote(remote);
                String provider = url.startsWith("https://gitlab.com/") ? "gitlab" : "github";
                prompt("Folder (blank uses projects/" + provider + "/<repo>)", "", directory ->
                        run("Cloning repository...", () -> store.add(url, directory), result -> { project = result; view = "project"; offset = 0; refresh(); }));
            } catch (Exception error) { fail(error); }
        });
    }
    private void addWorkspace() {
        prompt("Workspace name", "", name -> prompt("Existing workspace folder (absolute path)", "", directory ->
                run("Adding workspace...", () -> { store.addWorkspace(name.trim(), directory); return null; }, ignored -> init())));
    }
    private void changeFolder() {
        prompt("New main folder (existing files will be kept)", project.directory(), directory ->
                run("Linking main folder...", () -> store.changeDirectory(project, directory), result -> { project = result; review = null; refresh(); }));
    }
    private void token() {
        String host = profileHost;
        prompt((host.equals("gitlab.com") ? "GitLab" : "GitHub") + " token (saved; blank clears it)", "", value ->
                run("Saving token and checking permissions...", () -> {
                    store.token(host, value); return store.checkToken(host);
                }, result -> { message = result.message(); init(); }));
    }
    private void identity() {
        prompt("Email", store.authorEmail(), email -> prompt("Commit author name", store.authorName(), name ->
                run("Saving email...", () -> { store.identity(name, email); return null; }, ignored -> { message = "Email and author name saved."; init(); })));
    }
    private void openProfile() {
        returnView = view; returnOffset = offset;
        if (project != null) profileHost = project.host();
        navigate("profile");
    }
    private void profile() throws Exception {
        String provider = profileHost.equals("gitlab.com") ? "GitLab" : "GitHub";
        Object cycle = button(left, 42, 64, provider, () -> {
            profileHost = profileHost.equals("github.com") ? "gitlab.com" : "github.com";
            offset = 0; requestedOffset = -1; message = ""; init();
        });
        MacroScreenBridge.setTooltip(cycle, "Provider: " + provider + "\nClick to cycle GitHub, GitLab");
        button(left, 66, 52, "Email", this::identity);
        button(left, 90, 52, "Token", this::token);
        boolean saved = store.hasToken(profileHost);
        List<String> paragraphs = new ArrayList<>(List.of(
                "Email: " + (store.authorEmail().isBlank() ? "null" : store.authorEmail()),
                "Token: " + (saved ? "saved" : "null"), ""));
        if (profileHost.equals("github.com")) {
            paragraphs.add("Recommended to use a fine-grained personal access token");
            paragraphs.add("for the repository with Contents read/write.");
        } else paragraphs.add("Recommended to use a project access token with write_repository, or read_repository for pull only.");
        if (saved && store.tokenWarning(profileHost)) {
            paragraphs.add(""); paragraphs.add(store.tokenNote(profileHost));
        }
        List<String> lines = new ArrayList<>();
        for (String source : paragraphs) {
            String remaining = source;
            while (MacroScreenBridge.textWidth(screen, remaining) > listWidth) {
                int end = Math.min(remaining.length(), Math.max(1, listWidth / 6));
                while (end > 1 && MacroScreenBridge.textWidth(screen, remaining.substring(0, end)) > listWidth) end--;
                int space = remaining.lastIndexOf(' ', end);
                if (space > 0) end = space;
                lines.add(remaining.substring(0, end)); remaining = remaining.substring(end).stripLeading();
            }
            lines.add(remaining);
        }
        viewport(lines.size(), 120, 11);
        int y = 120;
        for (String line : visible(lines)) { labels.add(new Label(line, y, store.tokenWarning(profileHost) ? 0xFFFFCC88 : 0xFFBBBBBB)); y += 11; }
    }
    private void prompt(String title, String initial, Consumer<String> accept) {
        try { MacroScreenBridge.textPrompt(screen, title, initial, value -> pending = () -> accept.accept(value)); }
        catch (Exception error) { fail(error); }
    }
    private void confirm(String title, Runnable action) {
        try { MacroScreenBridge.confirm(screen, title, () -> pending = action); }
        catch (Exception error) { fail(error); }
    }
    private <T> void run(String status, Work<T> work, Consumer<T> done) {
        if (busy) return;
        busy = true; message = status; init();
        Thread worker = new Thread(() -> {
            try {
                T result = work.run();
                completions.add(() -> { busy = false; message = ""; done.accept(result); init(); });
            } catch (Exception error) {
                completions.add(() -> { busy = false; fail(error); init(); });
            }
        }, "SynapMc GitHub action");
        worker.setDaemon(true); worker.start();
    }
    private void fail(Exception error) {
        message = error.getMessage() == null ? "Could not complete GitHub action." : error.getMessage();
        // Errors are already credential-redacted by GitHubGit.
        SynapMcShared.error("[SynapMc] GitHub: " + message);
    }
    private void navigate(String destination) { view = destination; offset = 0; requestedOffset = -1; message = ""; init(); }
    private void back() {
        if (view.equals("profile")) { view = returnView; offset = returnOffset; message = ""; init(); }
        else if (view.equals("projects")) MacroScreenBridge.back(screen);
        else if (view.equals("diff") || view.equals("workspace")) navigate("project");
        else { project = null; review = null; navigate("projects"); }
    }
    public boolean scroll(double amount) {
        if (busy || amount == 0 || MacroScreenBridge.hasOverlay(screen)) return false;
        offset = Math.max(0, Math.min(Math.max(0, totalRows - visibleRows), offset - (int) Math.signum(amount)));
        init(); return true;
    }
    public void render(Object context) {
        // Native overlays must close before opening another prompt or rebuilding widgets.
        if (MacroScreenBridge.hasOverlay(screen)) return;
        Runnable completion;
        while ((completion = completions.poll()) != null) completion.run();
        if (!Objects.equals(gitAvailable, GitInstallation.available())) init();
        if (requestedOffset >= 0) { offset = requestedOffset; requestedOffset = -1; init(); }
        if (pending != null) { Runnable action = pending; pending = null; action.run(); }
        if (context == null || MacroScreenBridge.hasOverlay(screen)) return;
        for (Label label : labels) {
            MacroScreenBridge.drawText(context, screen, fit(label.text, listWidth), left, label.y, label.color);
        }
        if (!message.isEmpty()) {
            String first = message.lines().findFirst().orElse("");
            MacroScreenBridge.drawText(context, screen, fit(first, panelWidth), left, height - 46, 0xFFFFCC88);
        }
    }
    private String fit(String text, int available) {
        if (MacroScreenBridge.textWidth(screen, text) <= available) return text;
        int end = text.length();
        while (end > 0 && MacroScreenBridge.textWidth(screen, text.substring(0, end) + "…") > available) end--;
        return text.substring(0, end) + "…";
    }
}
