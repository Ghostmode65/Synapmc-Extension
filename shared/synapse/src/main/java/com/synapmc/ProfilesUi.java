package com.synapmc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Profile buttons and expandable, read-only registration text. */
public final class ProfilesUi {
    private final Object screen;
    private final List<Object> widgets = new ArrayList<>();
    private final List<TextLine> text = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final Set<String> expanded = new HashSet<>();
    private NativeProfiles.Snapshot snapshot;
    private Object nameInput;
    private int width, height, left, rowWidth, nameWidth, offset, visibleRows;
    private boolean editMode;
    private String message = "";
    private Runnable pendingAction;

    private record TextLine(String value, int x, int y, int color) {}
    private record Row(NativeProfiles.Entry profile, String detail, int color) {}

    public ProfilesUi(Object screen) { this.screen = screen; }

    public void init() {
        String draft = nameInput == null ? "" : MacroScreenBridge.inputValue(nameInput);
        for (Object widget : widgets) MacroScreenBridge.removeWidget(screen, widget);
        widgets.clear(); text.clear(); rows.clear();
        width = MacroScreenBridge.dimension(screen, "width", "field_22789");
        height = MacroScreenBridge.dimension(screen, "height", "field_22790");
        left = 12; rowWidth = width - 24; nameWidth = rowWidth / 2;
        visibleRows = Math.max(1, (height - 122) / 26);
        try {
            button(left + rowWidth - 64, height - 28, 64, 18, "Back", () -> MacroScreenBridge.back(screen));
            NativeProfiles.initialize(); snapshot = NativeProfiles.snapshot();
            expanded.retainAll(snapshot.entries().stream().map(NativeProfiles.Entry::name).toList());
            nameInput = MacroScreenBridge.addTextInput(screen, left, 38, rowWidth - 156, 18, ignored -> { });
            widgets.add(nameInput); MacroScreenBridge.inputText(nameInput, draft);
            MacroScreenBridge.setTooltip(nameInput, "Name for a new, empty profile");
            Object create = button(left + rowWidth - 152, 38, 64, 18, "Create", this::create);
            MacroScreenBridge.setTooltip(create, "Create an empty profile and switch to it");
            Object mode = button(left + rowWidth - 84, 38, 84, 18, editMode ? "Edit Mode: On" : "Edit Mode", () -> {
                editMode = !editMode; message = ""; init();
            });
            MacroScreenBridge.setTooltip(mode, "Click a profile name to Rename or Delete. Click here again to switch profiles normally.");

            for (NativeProfiles.Entry entry : snapshot.entries()) {
                rows.add(new Row(entry, null, 0xFFBBBBBB));
                if (!expanded.contains(entry.name())) continue;
                if (entry.registrations().isEmpty()) rows.add(new Row(entry, "No registered scripts", 0xFF777777));
                for (NativeProfiles.Registration registration : entry.registrations()) {
                    String label = registration.script() + " (" + registration.kind() + ": " + assignment(registration) + ")";
                    for (String line : wrap(label, rowWidth - 16))
                        rows.add(new Row(entry, line, registration.enabled() ? 0xFFBBBBBB : 0xFF888888));
                }
            }
            offset = Math.min(offset, Math.max(0, rows.size() - visibleRows));
            for (int i = offset; i < Math.min(rows.size(), offset + visibleRows); i++) {
                Row row = rows.get(i);
                int y = 76 + (i - offset) * 26;
                if (row.detail() != null) {
                    text.add(new TextLine(row.detail(), left + 16, y + 6, row.color()));
                    continue;
                }
                NativeProfiles.Entry entry = row.profile();
                Object name = button(left, y, nameWidth, 22, fit(entry.name(), nameWidth - 8), () -> select(entry.name(), y));
                MacroScreenBridge.setTooltip(name, (editMode ? "Rename or Delete " : "Switch to ") + entry.name());
                if (entry.name().equals(snapshot.current())) MacroScreenBridge.buttonColor(name, 0x405070A0);
                Object arrow = button(left + nameWidth + 4, y, 20, 22, expanded.contains(entry.name()) ? "↑" : "↓", () -> {
                    if (!expanded.add(entry.name())) expanded.remove(entry.name());
                    init();
                });
                MacroScreenBridge.setTooltip(arrow, "Show or hide registered scripts. Services are shared across profiles. Scroll for more rows.");
                String count = "Scripts: " + entry.bindings();
                int countWidth = MacroScreenBridge.textWidth(screen, count);
                int countRight = left + rowWidth - 156;
                if (countRight - countWidth < left + nameWidth + 40) countRight = left + rowWidth;
                text.add(new TextLine(count, countRight - countWidth, y + 6, 0xFFBBBBBB));
            }
        } catch (Exception error) { fail(error); }
    }

    private Object button(int x, int y, int w, int h, String caption, Runnable action) throws Exception {
        Object widget = MacroScreenBridge.addButton(screen, x, y, w, h, caption, action); widgets.add(widget); return widget;
    }

    private void create() {
        try {
            NativeProfiles.create(MacroScreenBridge.inputValue(nameInput));
            MacroScreenBridge.inputText(nameInput, ""); message = ""; offset = 0; init();
        } catch (Exception error) { fail(error); }
    }

    private void select(String name, int y) {
        try {
            if (editMode) {
                MacroScreenBridge.choiceMenu(screen, left, y + 22, List.of("Rename", "Delete"), choice -> {
                    if (choice == 0) pendingAction = () -> rename(name);
                    else if (choice == 1) pendingAction = () -> delete(name);
                });
            } else {
                NativeProfiles.switchTo(name); message = ""; init();
            }
        } catch (Exception error) { fail(error); }
    }

    private void rename(String name) {
        try {
            MacroScreenBridge.textPrompt(screen, "Rename profile", name, replacement -> pendingAction = () -> {
                try {
                    NativeProfiles.rename(name, replacement);
                    if (expanded.remove(name)) expanded.add(replacement.trim());
                    message = ""; init();
                } catch (Exception error) { fail(error); }
            });
        } catch (Exception error) { fail(error); }
    }

    private void delete(String name) {
        if (snapshot.entries().size() == 1) { message = "Keep at least one profile."; return; }
        try {
            MacroScreenBridge.confirm(screen, "Delete profile " + name + " and its bindings?", () -> pendingAction = () -> {
                try { NativeProfiles.delete(name); expanded.remove(name); message = ""; init(); }
                catch (Exception error) { fail(error); }
            });
        } catch (Exception error) { fail(error); }
    }

    public boolean keyPressed(int key) {
        if (MacroScreenBridge.hasOverlay(screen)) return false;
        if ((key == 257 || key == 335) && MacroScreenBridge.inputFocused(nameInput)) { create(); return true; }
        return false;
    }

    public boolean scroll(double amount) {
        if (amount == 0 || MacroScreenBridge.hasOverlay(screen) || snapshot == null) return false;
        offset = Math.max(0, Math.min(Math.max(0, rows.size() - visibleRows), offset - (int) Math.signum(amount)));
        init(); return true;
    }

    public void render(Object context) {
        if (pendingAction != null && !MacroScreenBridge.hasOverlay(screen)) {
            Runnable action = pendingAction; pendingAction = null; action.run();
        }
        if (MacroScreenBridge.hasOverlay(screen)) return;
        try { if (!NativeProfiles.snapshot().equals(snapshot)) init(); }
        catch (Exception error) { fail(error); }
        if (context == null) return;
        MacroScreenBridge.drawText(context, screen, "Profiles", left, 64, 0xFFBBBBBB);
        for (TextLine line : text) MacroScreenBridge.drawText(context, screen, line.value(), line.x(), line.y(), line.color());
        if (!message.isEmpty()) MacroScreenBridge.drawText(context, screen, fit(message, rowWidth), left, height - 38, 0xFFFF7777);
    }

    private static String assignment(NativeProfiles.Registration registration) {
        if (!registration.kind().equals("Key")) return registration.assignment();
        return registration.assignment().replace("key.keyboard.", "").replace("key.mouse.", "Mouse ").replace('.', ' ');
    }

    private List<String> wrap(String text, int limit) {
        List<String> lines = new ArrayList<>();
        while (MacroScreenBridge.textWidth(screen, text) > limit) {
            int end = 1;
            while (end < text.length() && MacroScreenBridge.textWidth(screen, text.substring(0, end + 1)) <= limit) end++;
            int space = text.lastIndexOf(' ', end);
            if (space > 0) end = space;
            lines.add(text.substring(0, end)); text = text.substring(end).stripLeading();
        }
        if (!text.isEmpty()) lines.add(text);
        return lines;
    }

    private String fit(String text, int limit) {
        if (MacroScreenBridge.textWidth(screen, text) <= limit) return text;
        while (!text.isEmpty() && MacroScreenBridge.textWidth(screen, text + "…") > limit) text = text.substring(0, text.length() - 1);
        return text + "…";
    }

    private void fail(Exception error) {
        String nextMessage = error.getMessage() == null ? "Could not update profiles. See log." : error.getMessage();
        if (!nextMessage.equals(message)) SynapMcShared.error("[SynapMc] Profiles: " + error);
        message = nextMessage;
    }
}
