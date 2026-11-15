package com.synapmc;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

public final class ScriptHistoryService {
    private static volatile ScriptHistory store;
    private static boolean started;
    private ScriptHistoryService() { }

    public static synchronized void start() {
        if (started) return;
        started = true;
        store = new ScriptHistory(SynapMcShared.roamingDir.toPath().resolve("scripts"));
        Thread monitor = new Thread(() -> {
            try { store.onLaunch(); }
            catch (Exception error) { SynapMcShared.error("[SynapMc] History startup: " + error); }
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    if (enabled()) {
                        Set<Path> roots = new LinkedHashSet<>();
                        roots.add(SynapMcShared.roamingDir.toPath().resolve("scripts"));
                        File macros = SynapMcShared.core.getMacroFolder();
                        if (macros != null) roots.add(macros.toPath());
                        for (String directory : new ArrayList<>(SynapMcShared.config.unifyFolders.values()))
                            roots.add(SynapMcShared.resolvePath(directory).toPath());
                        Set<String> extensions = new HashSet<>(List.of("js", "lua", "py", "rb", "ts", "kts", "groovy", "r"));
                        for (String extension : SynapMcShared.core.getAvailableLanguages().values())
                            extensions.add(extension.replaceFirst("^\\.", "").toLowerCase(Locale.ROOT));
                        store.scan(roots, extensions);
                    }
                    Thread.sleep(60_000);
                } catch (InterruptedException stopped) { Thread.currentThread().interrupt(); }
                catch (Exception error) {
                    SynapMcShared.error("[SynapMc] History scan: " + error);
                    try { Thread.sleep(60_000); }
                    catch (InterruptedException stopped) { Thread.currentThread().interrupt(); }
                }
            }
        }, "SynapMc script history");
        monitor.setDaemon(true);
        monitor.start();
    }

    public static boolean enabled() { return SynapMcShared.config != null && SynapMcShared.config.scriptHistory; }
    public static ScriptHistory store() { return store; }
    public static void capture(File file) {
        if (!enabled() || store == null || file == null) return;
        try { store.capture(file.toPath()); }
        catch (Exception error) { SynapMcShared.error("[SynapMc] Could not back up " + file + ": " + error); }
    }
}
