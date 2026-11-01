package com.synapmc;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class SnippetUi {
    private final Object screen;
    private final String extension;
    private final Consumer<String> insert;
    private SnippetStore store;
    private List<Path> files = List.of();
    private final List<Row> rows = new ArrayList<>();
    private Object filename, previous, next, editModeButton;
    private boolean editMode;
    private int page, pageSize, width, height;
    private int left, top, panelWidth, cellWidth, columns, gridTop, footerY;
    private Runnable pendingAction;
    private String message = "";

    public SnippetUi(Object screen, String extension, Consumer<String> insert) {
        this.screen = screen;
        this.extension = extension;
        this.insert = insert;
    }

    public void init() {
        width = MacroScreenBridge.dimension(screen, "width", "field_22789");
        height = MacroScreenBridge.dimension(screen, "height", "field_22790");
        panelWidth = Math.max(96, Math.min(560, Math.min(width - 24, width * 3 / 4)));
        left = (width - panelWidth) / 2;
        top = 40;
        footerY = height - 28;
        columns = Math.max(1, Math.min(5, panelWidth / 84));
        cellWidth = panelWidth / columns;
        gridTop = top + 38;
        pageSize = columns * Math.max(1, (footerY - 42 - gridTop) / 14);
        rows.clear();
        pendingAction = null;
        try {
            store = SnippetStore.open();
            files = store.list(extension);
            page = Math.min(page, Math.max(0, (files.size() - 1) / pageSize));
            int modeWidth = Math.min(84, panelWidth / 3);
            int createWidth = Math.min(62, panelWidth / 4);
            filename = MacroScreenBridge.addTextInput(screen, left, top + 14, panelWidth - createWidth - modeWidth - 8, 16, ignored -> { });
            MacroScreenBridge.setTooltip(filename, extension == null ? "Snippet filename, e.g. example.js or example.lua" : "Snippet filename (" + extension + ")");
            MacroScreenBridge.addButton(screen, left + panelWidth - createWidth - modeWidth - 4, top + 14, createWidth, 16, "Create", this::create);
            editModeButton = MacroScreenBridge.addButton(screen, left + panelWidth - modeWidth, top + 14, modeWidth, 16,
                    "Edit Mode", () -> { editMode = !editMode; refresh(); });
            for (int i = 0; i < pageSize; i++) {
                Row row = new Row();
                row.x = left + (i % columns) * cellWidth;
                row.y = gridTop + (i / columns) * 14;
                row.select = MacroScreenBridge.addButton(screen, row.x, row.y, cellWidth - 4, 12, "", () -> select(row));
                rows.add(row);
            }
            int navWidth = Math.min(54, (panelWidth - 12) / 3);
            previous = MacroScreenBridge.addButton(screen, left, footerY, navWidth, 16, navWidth < 54 ? "<" : "Previous", () -> changePage(-1));
            next = MacroScreenBridge.addButton(screen, left + navWidth + 4, footerY, navWidth, 16, navWidth < 54 ? ">" : "Next", () -> changePage(1));
            MacroScreenBridge.addButton(screen, left + panelWidth - navWidth, footerY, navWidth, 16, "Back", () -> MacroScreenBridge.back(screen));
            refresh();
        } catch (Exception error) { fail(error); }
    }

    private void create() {
        if (store == null) return;
        try {
            String name = MacroScreenBridge.inputValue(filename).trim();
            if (extension != null && SnippetStore.extension(name).isEmpty()) name += extension;
            if (extension != null && !SnippetStore.extension(name).equalsIgnoreCase(extension))
                throw new IOException("Use the current language extension: " + extension);
            Path path = store.create(name);
            files = store.list(extension);
            MacroScreenBridge.inputText(filename, "");
            refresh();
            edit(path);
        } catch (IOException error) { fail(error); }
    }

    private void edit(Path path) {
        if (path == null) return;
        try {
            store.read(path);
            if (!MinecraftAccess.openEditor(path.toFile(), 1, screen)) message = "Could not open snippet editor";
        } catch (IOException error) { fail(error); }
    }

    private void select(Row row) {
        if (row.path == null) return;
        if (editMode) { options(row); return; }
        if (insert == null) { edit(row.path); return; }
        try {
            insert.accept(store.read(row.path));
            MacroScreenBridge.back(screen);
        } catch (IOException | IllegalStateException error) { fail(error); }
    }

    private void options(Row row) {
        if (row.path == null) return;
        Path path = row.path;
        try {
            MacroScreenBridge.choiceMenu(screen, row.x, row.y + 12,
                    List.of("Edit", "Rename", "Delete"), index -> {
                if (index == 0) pendingAction = () -> edit(path);
                else if (index == 1) pendingAction = () -> rename(path);
                else if (index == 2) pendingAction = () -> delete(path);
            });
        } catch (Exception error) { fail(error); }
    }

    private void rename(Path path) {
        try {
            MacroScreenBridge.textPrompt(screen, "Rename snippet", path.getFileName().toString(), value -> {
                try {
                    String name = value.trim();
                    if (SnippetStore.extension(name).isEmpty()) name += SnippetStore.extension(path.toString());
                    if (extension != null && !SnippetStore.extension(name).equalsIgnoreCase(extension))
                        throw new IOException("Use the current language extension: " + extension);
                    Path renamed = store.rename(path, name);
                    files = store.list(extension);
                    page = files.indexOf(renamed) / pageSize;
                    message = "";
                    refresh();
                } catch (IOException error) { fail(error); }
            });
        } catch (Exception error) { fail(error); }
    }

    private void delete(Path path) {
        try {
            MacroScreenBridge.confirm(screen, "Delete snippet \"" + store.name(path) + "\"?", () -> {
                try {
                    store.delete(path);
                    files = store.list(extension);
                    page = Math.min(page, Math.max(0, (files.size() - 1) / pageSize));
                    message = "";
                    refresh();
                } catch (IOException error) { fail(error); }
            });
        } catch (Exception error) { fail(error); }
    }

    private void changePage(int amount) {
        page = Math.max(0, Math.min(Math.max(0, (files.size() - 1) / pageSize), page + amount));
        refresh();
    }

    public boolean scroll(double amount) {
        if (amount == 0 || MacroScreenBridge.hasOverlay(screen)) return false;
        changePage(amount > 0 ? -1 : 1);
        return true;
    }

    private void refresh() {
        MacroScreenBridge.caption(editModeButton, editMode ? "Edit Mode: On" : "Edit Mode");
        MacroScreenBridge.setTooltip(editModeButton, editMode ? "Click a snippet to Edit, Rename, or Delete. Click here to leave Edit Mode."
                : "Click here to manage snippets, then select a filename.");
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int index = page * pageSize + i;
            row.path = index < files.size() ? files.get(index) : null;
            MacroScreenBridge.visible(row.select, row.path != null);
            if (row.path == null) continue;
            MacroScreenBridge.caption(row.select, fit(store.name(row.path), cellWidth - 12));
            MacroScreenBridge.setTooltip(row.select, (editMode ? "Edit, Rename, or Delete " : insert == null ? "Edit " : "Insert ") + store.name(row.path));
        }
        MacroScreenBridge.visible(previous, page > 0);
        MacroScreenBridge.visible(next, (page + 1) * pageSize < files.size());
    }

    private String fit(String text, int pixels) {
        if (MacroScreenBridge.textWidth(screen, text) <= pixels) return text;
        while (!text.isEmpty() && MacroScreenBridge.textWidth(screen, text + "...") > pixels)
            text = text.substring(0, text.length() - 1);
        return text + "...";
    }

    private void fail(Exception error) {
        message = error.getMessage() == null ? "Could not load snippets" : error.getMessage();
        SynapMcShared.error("[SynapMc] Snippets: " + error);
    }

    public void render(Object context) {
        // Native menus close after their callback. Open the next dialog on the
        // following frame so it is not nested under a menu that is closing.
        if (pendingAction != null && !MacroScreenBridge.hasOverlay(screen)) {
            Runnable action = pendingAction;
            pendingAction = null;
            action.run();
            return;
        }
        if (MacroScreenBridge.hasOverlay(screen)) return;
        centered(context, ".jsmacros/scripts/snippets/" + (extension == null ? "" : " (" + extension + ")"), top, 0xFFBBBBBB);
        if (files.isEmpty()) centered(context, "No snippets yet. Enter a filename above to create one.", gridTop + 14, 0xFFBBBBBB);
        centered(context, message.isEmpty() ? "Use #hashtags to customize pasting" : message,
                footerY - 16, message.isEmpty() ? 0xFFBBBBBB : 0xFFFF7777);
        if (files.size() > pageSize) {
            String label = (page + 1) + " / " + ((files.size() + pageSize - 1) / pageSize);
            centered(context, label, footerY - 30, 0xFFBBBBBB);
        }
    }

    private void centered(Object context, String value, int y, int color) {
        value = fit(value, panelWidth);
        MacroScreenBridge.drawText(context, screen, value, left + (panelWidth - MacroScreenBridge.textWidth(screen, value)) / 2, y, color);
    }

    private static final class Row { Path path; int x, y; Object select; }
}
