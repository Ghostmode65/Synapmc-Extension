package com.synapmc;

import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class ScriptHistoryUi {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private final Object screen, editor;
    private final List<Object> widgets = new ArrayList<>();
    private List<ScriptHistory.Version> versions = List.of();
    private String expected, message = "", filename = "";
    private int left, width, top, rowHeight, footer;
    private Runnable pending;

    public ScriptHistoryUi(Object screen, Object editor) { this.screen = screen; this.editor = editor; }

    public void init() {
        for (Object widget : widgets) MacroScreenBridge.removeWidget(screen, widget);
        widgets.clear();
        width = Math.min(400, MacroScreenBridge.dimension(screen, "width", "field_22789") - 24);
        left = (MacroScreenBridge.dimension(screen, "width", "field_22789") - width) / 2;
        int height = MacroScreenBridge.dimension(screen, "height", "field_22790");
        top = 44; footer = height - 28;
        rowHeight = Math.min(30, Math.max(18, (height - 110) / 5));
        try {
            File file = (File) EditorHistory.field(editor, "file");
            filename = file.getName();
            if (expected == null) expected = (String) EditorHistory.field(EditorHistory.field(editor, "history"), "current");
            ScriptHistory store = ScriptHistoryService.store();
            versions = store == null ? List.of() : store.versions(file.toPath());
            int y = top + 18;
            for (ScriptHistory.Version version : versions) {
                Object button = MacroScreenBridge.addButton(screen, left, y, width, rowHeight - 2,
                        version.label() + " · " + DATE.format(Instant.ofEpochMilli(version.timestamp())), () -> load(version));
                MacroScreenBridge.setTooltip(button, "Load into editor for review. Save to keep it; Undo to revert.");
                widgets.add(button); y += rowHeight;
            }
            int backWidth = Math.min(90, width);
            widgets.add(MacroScreenBridge.addButton(screen, left + width - backWidth, footer, backWidth, 18,
                    "Back", () -> MacroScreenBridge.back(screen)));
        } catch (Exception error) { fail(error); }
    }

    private void load(ScriptHistory.Version version) {
        try {
            MacroScreenBridge.confirm(screen, "Load " + version.label() + "? Replaces editor text; Undo is available. Save to keep it.",
                    () -> pending = () -> {
                        try {
                            String replacement = Files.readString(version.snapshot());
                            if (!EditorHistory.restore(editor, expected, replacement)) {
                                message = "Editor changed. Reopen History and try again.";
                                return;
                            }
                            MacroScreenBridge.back(screen);
                        } catch (Exception error) { fail(error); }
                    });
        } catch (Exception error) { fail(error); }
    }

    public void render(Object context) {
        if (pending != null && !MacroScreenBridge.hasOverlay(screen)) {
            Runnable action = pending; pending = null; action.run(); return;
        }
        if (MacroScreenBridge.hasOverlay(screen)) return;
        text(context, filename, top, 0xFFBBBBBB);
        if (versions.isEmpty()) text(context, "No backups old enough yet.", top + 24, 0xFFBBBBBB);
        text(context, message.isEmpty() ? (ScriptHistoryService.enabled()
                ? "Select a version to load into the editor." : "Script History is off. Existing backups remain available.") : message,
                footer + 5, message.isEmpty() ? 0xFFBBBBBB : 0xFFFF7777, width - Math.min(90, width) - 8);
    }

    private void text(Object context, String text, int y, int color) {
        text(context, text, y, color, width);
    }
    private void text(Object context, String text, int y, int color, int availableWidth) {
        while (!text.isEmpty() && MacroScreenBridge.textWidth(screen, text) > availableWidth) text = text.substring(0, text.length() - 1);
        MacroScreenBridge.drawText(context, screen, text, left + (availableWidth - MacroScreenBridge.textWidth(screen, text)) / 2, y, color);
    }
    private void fail(Exception error) {
        message = "Could not load history. See log.";
        SynapMcShared.error("[SynapMc] History: " + error);
    }
}
