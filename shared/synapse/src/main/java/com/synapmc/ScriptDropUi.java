package com.synapmc;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ScriptDropUi {
    private final Object screen;
    private final List<File> files = new ArrayList<File>();
    private final List<Line> text = new ArrayList<Line>();
    private final List<RowControls> rowControls = new ArrayList<RowControls>();
    private final Map<String, String> languageOverrides = new LinkedHashMap<String, String>();
    private final Map<String, String> nameOverrides = new LinkedHashMap<String, String>();
    private final Map<String, String> sourceFolders = new LinkedHashMap<>();
    private String destination = "global/imported/";
    private Object destinationButton;
    private Object destinationInput;
    private boolean editingDestination;
    private String editingSource;
    private boolean attempted;
    private Object registerButton;
    private String registerKind = "";
    private boolean registerScript;
    private boolean busy;
    private int scrollOffset;
    private int visibleRows;
    private String status = "";

    public ScriptDropUi(Object screen) { this.screen = screen; }

    public void init() {
        text.clear();
        int width = MacroScreenBridge.dimension(screen, "width", "field_22789");
        int height = MacroScreenBridge.dimension(screen, "height", "field_22790");
        int buttonY = height - 26;
        visibleRows = Math.max(1, (height - 116) / 20);
        int slot = Math.max(54, (width - 16) / 4);
        try {
            MacroScreenBridge.addButton(screen, 8, buttonY, slot - 4, 20, "Choose files", this::choose);
            MacroScreenBridge.addButton(screen, 8 + slot, buttonY, slot - 4, 20, "Import", () -> submit(true));
            MacroScreenBridge.addButton(screen, 8 + 2 * slot, buttonY, slot - 4, 20, "Execute", () -> submit(false));
            MacroScreenBridge.addButton(screen, 8 + 3 * slot, buttonY, slot - 4, 20,
                    "Back", () -> MacroScreenBridge.back(screen));
            MacroScreenBridge.addButton(screen, 8, height - 51, 54, 18, "Clear", () -> {
                if (busy) return;
                files.clear();
                languageOverrides.clear();
                nameOverrides.clear();
                sourceFolders.clear();
                editingSource = null;
                attempted = false;
                scrollOffset = 0;
                status = "Selection cleared.";
                refresh();
            });
            int backX = 8 + 3 * slot;
            MacroScreenBridge.addButton(screen, backX - slot, height - 51, slot - 4, 18, "Export Keys", () -> transferKeys(true));
            destinationButton = MacroScreenBridge.addTextButton(screen, 8, 40, Math.max(120, width - 16), 18,
                    "Destination: " + destination, () -> {
                        if (busy) return;
                        finishEditing();
                        editingDestination = true;
                        MacroScreenBridge.inputText(destinationInput, destination);
                        refresh();
                        MacroScreenBridge.focusInput(screen, destinationInput);
                    });
            destinationInput = MacroScreenBridge.addTextInput(screen, 8, 40, Math.max(120, width - 16), 18,
                    value -> { if (editingDestination) destination = value; });
            MacroScreenBridge.addButton(screen, backX, height - 51, slot - 4, 18, "Import Keys", () -> transferKeys(false));
            registerKind = MacroScreenBridge.importerContext(screen);
            if ("key".equals(registerKind) || "event".equals(registerKind) || "service".equals(registerKind)) {
                int registerWidth = Math.min(132, slot - 4);
                registerButton = MacroScreenBridge.addButton(screen, 8 + slot, height - 51, registerWidth, 18,
                        registerLabel(), () -> {
                            if (!busy) {
                                registerScript = !registerScript;
                                MacroScreenBridge.caption(registerButton, registerLabel());
                            }
                        });
            }
            rowControls.clear();
            int rowWidth = Math.min(600, width - 24);
            int left = (width - rowWidth) / 2;
            int languageWidth = Math.min(100, rowWidth / 4);
            int nameWidth = rowWidth - languageWidth - 106;
            for (int row = 0; row < visibleRows; row++) {
                final RowControls controls = new RowControls();
                int y = 60 + row * 20;
                controls.remove = MacroScreenBridge.addButton(screen, left, y, 20, 18, "X", () -> {
                    if (busy || controls.entry == null) return;
                    String key = sourceKey(controls.entry);
                    files.removeIf(file -> file.toPath().toAbsolutePath().normalize().toString().equals(key));
                    nameOverrides.remove(key); languageOverrides.remove(key); sourceFolders.remove(key);
                    if (key.equals(editingSource)) editingSource = null;
                    refresh();
                });
                int nameX = left + 24;
                controls.name = MacroScreenBridge.addButton(screen, nameX, y, nameWidth, 18, "", () -> { });
                controls.rename = MacroScreenBridge.addTextInput(screen, nameX, y, nameWidth, 18, value -> {
                    if (busy || controls.entry == null || !sourceKey(controls.entry).equals(editingSource)) return;
                    nameOverrides.put(sourceKey(controls.entry), value);
                    refresh();
                });
                controls.status = MacroScreenBridge.addButton(screen, nameX + nameWidth + 4, y, 24, 18,
                        "✎", () -> toggleRename(controls));
                controls.edit = MacroScreenBridge.addButton(screen, nameX + nameWidth + 32, y, 42, 18, "Edit", () -> {
                    if (!busy && controls.entry != null) MinecraftAccess.openEditor(controls.entry.source, 1, screen);
                });
                controls.language = MacroScreenBridge.addButton(screen, nameX + nameWidth + 78, y,
                        languageWidth, 18, "", () -> cycleLanguage(controls));
                rowControls.add(controls);
            }
        } catch (Exception error) {
            SynapMcShared.error("[SynapMc] Could not initialize script screen: " + error);
        }
        refresh();
    }

    public void drop(List<Path> paths) {
        if (paths == null || busy) return;
        int added = 0;
        for (Path path : paths) {
            if (path != null) { added += addSelection(path.toFile()); }
        }
        if (added > 0) {
            scrollOffset = 0;
            status = added + (added == 1 ? " file added." : " files added.");
            refresh();
        }
    }

    private void choose() {
        if (busy) return;
        Thread chooser = new Thread(() -> {
            try {
                Class<?> pointerBuffer = Class.forName("org.lwjgl.PointerBuffer");
                Method open = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs")
                        .getMethod("tinyfd_openFileDialog", CharSequence.class, CharSequence.class,
                                pointerBuffer, CharSequence.class, boolean.class);
                String selection = (String) open.invoke(null, "Choose scripts for JsMacros", null,
                        null, null, true);
                if (selection != null && !selection.isEmpty()) MacroScreenBridge.onClient(() -> {
                    String[] selected = selection.split("\\|");
                    for (String path : selected) addSelection(new File(path));
                    scrollOffset = 0;
                    status = selected.length + (selected.length == 1 ? " file added." : " files added.");
                    refresh();
                });
            } catch (Exception error) {
                MacroScreenBridge.onClient(() -> {
                    status = "File chooser failed: " + error.getMessage();
                    refresh();
                });
            }
        }, "SynapMc file chooser");
        chooser.setDaemon(true);
        chooser.start();
    }

    private int addSelection(File selection) {
        try {
            List<File> expanded = ScriptDropService.expandFiles(selection);
            for (File file : expanded) {
                if (!files.contains(file)) files.add(file);
                if (selection.isDirectory()) sourceFolders.put(file.toPath().toAbsolutePath().normalize().toString(),
                        selection.toPath().toAbsolutePath().getParent().relativize(file.toPath().toAbsolutePath().getParent()).toString());
            }
            return expanded.size();
        } catch (Exception error) { status = "Could not read folder: " + error.getMessage(); return 0; }
    }

    public boolean keyPressed(int key) {
        if ((key == 257 || key == 335) && (editingSource != null || editingDestination)) { finishEditing(); return true; }
        return false;
    }

    private void finishEditing() {
        for (RowControls controls : rowControls) {
            if (controls.entry != null && sourceKey(controls.entry).equals(editingSource))
                nameOverrides.put(editingSource, MacroScreenBridge.inputValue(controls.rename));
        }
        editingSource = null;
        if (editingDestination) {
            destination = MacroScreenBridge.inputValue(destinationInput).replace('\\', '/').trim();
            if (!destination.equals("global") && !destination.startsWith("global/")) destination = "global/" + destination;
            editingDestination = false;
        }
        refresh();
    }

    private void submit(boolean importing) {
        if (busy) return;
        finishEditing();
        attempted = true;
        List<ScriptDropService.Entry> entries = preview();
        int valid = 0;
        for (ScriptDropService.Entry entry : entries) if (entry.problem == null) valid++;
        if (valid == 0) {
            status = "No scripts ready. Check highlighted rows.";
            refresh();
            return;
        }
        busy = true;
        status = importing ? "Importing " + valid + " file(s)..." : "Executing " + valid + " file(s)...";
        refresh();
        Thread worker = new Thread(() -> {
            int success = 0;
            int failed = 0;
            java.util.Set<String> failedSources = new java.util.HashSet<String>();
            for (ScriptDropService.Entry entry : entries) {
                if (entry.problem != null) {
                    failed++;
                    failedSources.add(sourceKey(entry));
                    SynapMcShared.error("[SynapMc] Could not process " + entry.source + ": " + entry.problem);
                    continue;
                }
                try {
                    if (importing) {
                        Path imported = ScriptDropService.importFile(entry);
                        if (registerScript && !ScriptDropService.registerImportedScript(imported.toFile(), registerKind)) {
                            failed++;
                            failedSources.add(sourceKey(entry));
                            SynapMcShared.error("[SynapMc] Imported but could not register " + imported);
                            continue;
                        }
                        success++;
                    } else if (ScriptDropService.executeFile(entry)) success++;
                    else { failed++; failedSources.add(sourceKey(entry)); }
                } catch (Exception error) {
                    failed++;
                    failedSources.add(sourceKey(entry));
                    SynapMcShared.error("[SynapMc] Could not import " + entry.source + ": " + error);
                }
            }
            final int completed = success;
            final int errors = failed;
            MacroScreenBridge.onClient(() -> {
                busy = false;
                if (importing) {
                    files.removeIf(file -> !failedSources.contains(file.toPath().toAbsolutePath().normalize().toString()));
                    languageOverrides.keySet().retainAll(failedSources);
                    nameOverrides.keySet().retainAll(failedSources);
                    sourceFolders.keySet().retainAll(failedSources);
                    scrollOffset = 0;
                }
                status = (importing ? "Imported " : "Started ") + completed + " file(s)"
                        + (errors == 0 ? "." : "; " + errors + " failed. See log.");
                refresh();
            });
        }, "SynapMc script action");
        worker.setDaemon(true);
        worker.start();
    }

    private String registerLabel() { return "Register script: " + (registerScript ? "On" : "Off"); }

    private void transferKeys(boolean exporting) {
        if (busy) return;
        busy = true;
        status = exporting ? "Exporting registrations..." : "Importing registrations...";
        refresh();
        Thread worker = new Thread(() -> {
            String result;
            try {
                Path directory = SynapMcShared.roamingDir.toPath().resolve("config/keys");
                java.nio.file.Files.createDirectories(directory);
                Class<?> dialogs = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
                String selected;
                if (exporting) selected = (String) dialogs.getMethod("tinyfd_saveFileDialog", CharSequence.class,
                        CharSequence.class, Class.forName("org.lwjgl.PointerBuffer"), CharSequence.class)
                        .invoke(null, "Export keys", directory.resolve("keys.json").toString(), null, "JSON files");
                else selected = (String) dialogs.getMethod("tinyfd_openFileDialog", CharSequence.class,
                        CharSequence.class, Class.forName("org.lwjgl.PointerBuffer"), CharSequence.class, boolean.class)
                        .invoke(null, "Import keys", directory.toString() + File.separator, null, "JSON files", false);
                result = selected == null ? "Registration transfer canceled."
                        : exporting ? KeyBindingsTransfer.exportKeys(Path.of(selected)) : KeyBindingsTransfer.importKeys(Path.of(selected));
            } catch (Exception error) {
                result = "Registration transfer failed. See log.";
                SynapMcShared.error("[SynapMc] Registration transfer failed: " + error);
            }
            final String message = result;
            MacroScreenBridge.onClient(() -> { busy = false; status = message; refresh(); });
        }, "SynapMc registration transfer");
        worker.setDaemon(true);
        worker.start();
    }

    public static boolean scrollScreen(Object screen, double amount) {
        if (!(screen instanceof ScriptDropScreenAccess)) return false;
        ((ScriptDropScreenAccess) screen).scroll(amount);
        return true;
    }

    public void scroll(double amount) {
        if (amount == 0) return;
        scrollOffset = Math.max(0, scrollOffset - (int) Math.signum(amount) * 3);
        refresh();
    }

    private void refresh() {
        text.clear();
        int width = MacroScreenBridge.dimension(screen, "width", "field_22789");
        int height = MacroScreenBridge.dimension(screen, "height", "field_22790");
        if (width == 0 || height == 0) return;
        if (!status.isEmpty()) line(status, 8, 28, 0xFFFFFFFF);
        MacroScreenBridge.caption(destinationButton, "Destination: " + destination);
        MacroScreenBridge.visible(destinationButton, !editingDestination);
        MacroScreenBridge.visible(destinationInput, editingDestination);
        for (RowControls controls : rowControls) {
            controls.entry = null;
            controls.visible(false);
        }
        if (files.isEmpty()) {
            line("Drag and drop scripts here", Math.max(8, width / 2 - 82), Math.max(64, height / 2 - 10), 0xFFAAAAAA);
            return;
        }
        List<ScriptDropService.Entry> entries = preview();
        line("Selected: " + entries.size(), 8, 8, 0xFFBBBBBB);
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, entries.size() - visibleRows)));
        int start = scrollOffset;
        for (int row = 0; row < rowControls.size(); row++) {
            RowControls controls = rowControls.get(row);
            int index = start + row;
            controls.entry = index < entries.size() ? entries.get(index) : null;
            if (controls.entry == null) continue;
            controls.visible(true);
            ScriptDropService.Entry entry = controls.entry;
            MacroScreenBridge.caption(controls.name, ScriptDropService.displayName(entry));
            String key = sourceKey(entry);
            boolean editing = key.equals(editingSource);
            MacroScreenBridge.visible(controls.name, !editing);
            MacroScreenBridge.visible(controls.rename, editing);
            if (editing) MacroScreenBridge.inputText(controls.rename,
                    nameOverrides.getOrDefault(key, entry.source.getName()));
            MacroScreenBridge.setTooltip(controls.name, entry.source.getAbsolutePath() + " (" + entry.characters + " chars)");
            MacroScreenBridge.setTooltip(controls.status, editing ? "Finish renaming" : "Rename imported file");
            MacroScreenBridge.setTooltip(controls.language, entry.problem == null
                    ? "Language is installed: " + entry.language : entry.problem);
            MacroScreenBridge.caption(controls.language, languageLabel(entry) + " ↻");
            int color = attempted && entry.problem != null ? 0xFFFF5555 : 0xFFFFFFFF;
            for (Object widget : new Object[] {controls.remove, controls.name, controls.rename, controls.status, controls.edit, controls.language})
                MacroScreenBridge.textColor(widget, color);
        }
        if (entries.size() > visibleRows) {
            int trackRows = visibleRows;
            int thumb = Math.max(1, trackRows * visibleRows / entries.size());
            int thumbAt = (trackRows - thumb) * scrollOffset / Math.max(1, entries.size() - visibleRows);
            for (int row = 0; row < trackRows; row++)
                line(row >= thumbAt && row < thumbAt + thumb ? "█" : "│", (width + Math.min(600, width - 24)) / 2 + 3, 60 + row * 20, 0xFFAAAAAA);
        }
    }

    private static String languageLabel(ScriptDropService.Entry entry) {
        if (entry.language != null) return entry.language;
        String name = entry.source.getName();
        int dot = name.lastIndexOf('.');
        return dot >= 0 && dot + 1 < name.length() ? name.substring(dot + 1) : "Original";
    }

    private List<ScriptDropService.Entry> preview() {
        return ScriptDropService.preview(files, languageOverrides, nameOverrides, destination, sourceFolders);
    }

    private static String sourceKey(ScriptDropService.Entry entry) {
        return entry.source.toPath().toAbsolutePath().normalize().toString();
    }

    private void toggleRename(RowControls controls) {
        if (busy || controls.entry == null) return;
        String key = sourceKey(controls.entry);
        if (key.equals(editingSource)) { finishEditing(); return; }
        if (editingSource != null || editingDestination) finishEditing();
        editingSource = key;
        refresh();
        if (editingSource != null) MacroScreenBridge.focusInput(screen, controls.rename);
    }

    private void cycleLanguage(RowControls controls) {
        if (busy || controls.entry == null || SynapMcShared.core == null) return;
        String key = controls.entry.source.toPath().toAbsolutePath().normalize().toString();
        String original = null;
        try { original = SynapMcShared.core.getLanguageForFile(controls.entry.source); }
        catch (RuntimeException ignored) { }
        List<String> languages = new ArrayList<String>();
        languages.add(original);
        for (String language : SynapMcShared.core.getAvailableLanguages().keySet())
            if (!languages.contains(language)) languages.add(language);
        String next = languages.get((languages.indexOf(controls.entry.language) + 1) % languages.size());
        if (java.util.Objects.equals(next, original)) languageOverrides.remove(key);
        else languageOverrides.put(key, next);
        refresh();
    }

    private void line(String value, int x, int y, int color) {
        text.add(new Line(value, x, y, color));
    }

    public void render(Object context) {
        if (editingDestination && !MacroScreenBridge.inputFocused(destinationInput)) finishEditing();
        if (editingSource != null) for (RowControls controls : rowControls) {
            if (controls.entry != null && sourceKey(controls.entry).equals(editingSource)
                    && !MacroScreenBridge.inputFocused(controls.rename)) { finishEditing(); break; }
        }
        for (Line line : text) MacroScreenBridge.drawText(context, screen, line.value, line.x, line.y, line.color);
    }

    public interface ScriptDropScreenAccess { void scroll(double amount); }

    private static final class Line {
        final String value;
        final int x;
        final int y;
        final int color;

        Line(String value, int x, int y, int color) {
            this.value = value;
            this.x = x;
            this.y = y;
            this.color = color;
        }
    }

    private static final class RowControls {
        Object remove;
        Object name;
        Object rename;
        Object status;
        Object edit;
        Object language;
        ScriptDropService.Entry entry;

        void visible(boolean visible) {
            MacroScreenBridge.visible(remove, visible);
            MacroScreenBridge.visible(name, visible);
            MacroScreenBridge.visible(rename, false);
            MacroScreenBridge.visible(status, visible);
            MacroScreenBridge.visible(edit, visible);
            MacroScreenBridge.visible(language, visible);
        }
    }
}
