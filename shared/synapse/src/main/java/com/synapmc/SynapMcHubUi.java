package com.synapmc;

import com.synapmc.library.FSynapse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Native hub with a fixed navigation rail and settings in the right-hand panel. */
public final class SynapMcHubUi {
    private final Object screen;
    private final List<Object> widgets = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private final Map<String, Object> inputs = new LinkedHashMap<>();
    private final FSynapse settings = new FSynapse(null, null);
    private String page = "General";
    private String message = "";
    private int width, height, rail, contentX, contentWidth, rowY;
    private int folderPage;
    private Map<String, String> drafts = Map.of();
    private Runnable pendingAction;

    public SynapMcHubUi(Object screen) { this.screen = screen; }

    public void init() {
        drafts = new LinkedHashMap<>();
        for (var entry : inputs.entrySet()) drafts.put(entry.getKey(), MacroScreenBridge.inputValue(entry.getValue()));
        for (Object widget : widgets) MacroScreenBridge.removeWidget(screen, widget);
        widgets.clear(); labels.clear(); inputs.clear();
        width = MacroScreenBridge.dimension(screen, "width", "field_22789");
        height = MacroScreenBridge.dimension(screen, "height", "field_22790");
        rail = Math.max(76, Math.min(120, width / 4));
        contentX = rail + 24;
        contentWidth = Math.max(80, width - contentX - 24);
        rowY = 66;
        try {
            int y = 42;
            int step = Math.min(22, Math.max(18, (height - 100) / 8));
            for (String name : List.of("Snippets", "Import", "Repository")) {
                String action = name;
                button(12, y, rail - 12, 18, name, () -> {
                    if (!saveDraft()) return;
                    if (action.equals("Snippets")) EditorSnippets.open(screen, null, null);
                    else if (action.equals("Import")) MacroScreenBridge.openImporter(screen);
                    else MacroScreenBridge.openRepoBrowser();
                });
                y += step;
            }
            y += 12;
            for (String name : List.of("General", "TCP Bridge", "Editor", "Unify")) {
                String tab = name;
                Object control = button(12, y, rail - 12, 18, name, () -> {
                    if (!saveDraft()) return;
                    page = tab; message = ""; init();
                });
                if (page.equals(name)) MacroScreenBridge.buttonColor(control, 0x885A80B0);
                y += step;
            }
            button(12, y + 8, rail - 12, 18, "Profiles", () -> {
                if (saveDraft()) MacroScreenBridge.openProfiles(screen);
            });
            button(12, height - 28, rail - 12, 18, "Back", () -> {
                if (saveDraft()) MacroScreenBridge.back(screen);
            });
            switch (page) {
                case "General" -> {
                    toggle("Auto-execute", "autorunEnabled");
                    toggle("Silence Logs", "silent");
                    toggle("Chat Say", "chatSayEnabled");
                    input("Mini Logs", "miniLogs");
                    input("Download cap (MiB)", "downloadMaxMb");
                }
                case "TCP Bridge" -> {
                    toggle("Enable TCP bridge", "tcpPortEnabled");
                    input("Port", "tcpPort");
                    input("Password", "tcpPortPassword");
                    input("Bind address", "tcpBindAddress");
                    toggle("Pre-authorization", "clientInfoPreAuth");
                    toggle("Log Executed", "logExecuted");
                }
                case "Editor" -> {
                    toggle("Script History", "scriptHistory");
                    input("Highlight Alpha (0–255)", "highlightAlpha");
                    input("Highlight Brightness (%)", "highlightBrightness");
                    label("Color palettes are saved in the color picker.", contentX, rowY + 8, 0xFFABB9CC);
                }
                case "Unify" -> folders();
                default -> { }
            }
            if (!inputs.isEmpty()) button(contentX, height - 28, Math.min(110, contentWidth), 18,
                    "Save settings", () -> { if (saveDraft()) { message = "Settings saved."; init(); } });
        } catch (Exception error) { message = "Could not open controls. See log."; SynapMcShared.error("[SynapMc] Hub: " + error); }
    }

    private Object button(int x, int y, int w, int h, String text, Runnable action) throws Exception {
        Object widget = MacroScreenBridge.addButton(screen, x, y, w, h, text, action);
        widgets.add(widget);
        return widget;
    }

    private void toggle(String title, String key) throws Exception {
        boolean value = (Boolean) settings.getConfig().get(key);
        int controlWidth = Math.min(64, contentWidth / 3);
        label(title, contentX, rowY + 4, 0xFFE3EAF4);
        button(contentX + contentWidth - controlWidth, rowY, controlWidth, 18, value ? "On" : "Off", () -> {
            if (!saveDraft()) return;
            if (!settings.saveConfig(key, !value)) message = "Could not save " + title + ".";
            else message = "";
            init();
        });
        rowY += 25;
    }

    private void input(String title, String key) throws Exception {
        int controlWidth = Math.min(130, Math.max(54, contentWidth / 3));
        boolean stacked = MacroScreenBridge.textWidth(screen, title) > contentWidth - controlWidth - 8;
        label(title, contentX, rowY + (stacked ? 0 : 4), 0xFFE3EAF4);
        if (stacked) rowY += 12;
        Object control = MacroScreenBridge.addTextInput(screen, contentX + contentWidth - controlWidth,
                rowY, controlWidth, 18, ignored -> { });
        MacroScreenBridge.inputText(control, drafts.getOrDefault(key, String.valueOf(settings.getConfig().get(key))));
        MacroScreenBridge.setTooltip(control, title);
        inputs.put(key, control); widgets.add(control);
        rowY += 25;
    }

    public boolean keyPressed(int key) {
        if (key != 257 && key != 335 || MacroScreenBridge.hasOverlay(screen)) return false;
        if (saveDraft()) { message = "Settings saved."; init(); }
        return true;
    }

    private boolean saveDraft() {
        Map<String, Object> values = new LinkedHashMap<>();
        try {
            for (var entry : inputs.entrySet()) {
                String key = entry.getKey();
                String text = MacroScreenBridge.inputValue(entry.getValue());
                if (key.equals("tcpPortPassword") || key.equals("tcpBindAddress")) values.put(key, text);
                else {
                    int number = Integer.parseInt(text.trim());
                    int minimum = key.equals("tcpPort") || key.equals("downloadMaxMb") ? 1 : 0;
                    int maximum = switch (key) {
                        case "tcpPort" -> 65535;
                        case "downloadMaxMb" -> 4096;
                        case "highlightAlpha" -> 255;
                        case "highlightBrightness" -> 100;
                        default -> Integer.MAX_VALUE;
                    };
                    if (number < minimum || number > maximum) throw new IllegalArgumentException();
                    values.put(key, number);
                }
            }
        } catch (IllegalArgumentException error) { message = "Enter valid values before saving."; return false; }
        Map<String, Object> current = settings.getConfig();
        for (var entry : values.entrySet())
            if (!java.util.Objects.equals(current.get(entry.getKey()), entry.getValue())
                    && !settings.saveConfig(entry.getKey(), entry.getValue())) {
                message = "Could not save settings. See log."; return false;
            }
        return true;
    }

    private void folders() throws Exception {
        Map<String, String> folders = SynapMcSettingsUi.unifiedFolders();
        int pageSize = Math.max(1, (height - 136) / 27);
        List<String> names = new ArrayList<>(folders.keySet());
        folderPage = Math.min(folderPage, Math.max(0, (names.size() - 1) / pageSize));
        button(contentX + contentWidth - Math.min(94, contentWidth / 2), 42, Math.min(94, contentWidth / 2), 18,
                "Add folder", () -> promptFolder(null));
        for (String name : names.subList(folderPage * pageSize, Math.min(names.size(), (folderPage + 1) * pageSize))) {
            Object edit = button(contentX, rowY, contentWidth - 26, 22, name + " → " + folders.get(name), () -> promptFolder(name));
            MacroScreenBridge.setTooltip(edit, name + " → " + folders.get(name));
            button(contentX + contentWidth - 22, rowY, 22, 22, "X", () -> {
                try { MacroScreenBridge.confirm(screen, "Remove link " + name + "?", () -> {
                    try { SynapMcSettingsUi.unifiedFolders().remove(name); message = "Link removed."; }
                    catch (IllegalArgumentException error) { message = error.getMessage(); }
                    pendingAction = this::init;
                }); } catch (Exception error) { message = "Could not open confirmation."; }
            });
            rowY += 27;
        }
        button(contentX, height - 28, 28, 18, "<", () -> { folderPage = Math.max(0, folderPage - 1); init(); });
        button(contentX + 32, height - 28, 28, 18, ">", () -> {
            if ((folderPage + 1) * pageSize < names.size()) folderPage++;
            init();
        });
    }

    private void promptFolder(String existing) {
        try {
            if (existing == null) MacroScreenBridge.textPrompt(screen, "Folder name", "", name -> {
                if (!name.isBlank()) pendingAction = () -> promptDirectory(name.trim());
            });
            else promptDirectory(existing);
        } catch (Exception error) { message = "Could not open folder editor."; }
    }

    private void promptDirectory(String name) {
        try {
            MacroScreenBridge.textPrompt(screen, "Shared directory for " + name,
                    SynapMcSettingsUi.unifiedFolders().getOrDefault(name, "scripts/" + name), directory -> {
                try { SynapMcSettingsUi.unifiedFolders().put(name, directory); message = "Folder saved."; }
                catch (IllegalArgumentException error) { message = error.getMessage(); }
                pendingAction = this::init;
            });
        } catch (Exception error) { message = "Could not open directory editor."; }
    }

    private void label(String text, int x, int y, int color) { labels.add(new Label(text, x, y, color)); }

    public void renderBackground(Object context) {
        if (context == null) return;
        GuiPaint.fill(context, screen, 0, 0, width, height, 0xFF0D1420);
        GuiPaint.fill(context, screen, 6, 30, rail + 8, height - 6, 0xE0182333);
        GuiPaint.fill(context, screen, rail + 12, 30, width - 8, height - 6, 0xD0111926);
        GuiPaint.fill(context, screen, rail + 12, 30, width - 8, 32, 0xFF709ACB);
    }

    public void render(Object context) {
        if (pendingAction != null && !MacroScreenBridge.hasOverlay(screen)) {
            Runnable action = pendingAction; pendingAction = null; action.run();
        }
        if (context == null || MacroScreenBridge.hasOverlay(screen)) return;
        MacroScreenBridge.drawText(context, screen, "SynapMc", 12, 16, 0xFFFFFFFF);
        MacroScreenBridge.drawText(context, screen, page, contentX, 44, 0xFFFFFFFF);
        for (Label label : labels) MacroScreenBridge.drawText(context, screen,
                fit(label.text, width - label.x - 12), label.x, label.y, label.color);
        if (!message.isEmpty()) MacroScreenBridge.drawText(context, screen,
                fit(message, contentWidth), contentX, height - 43, 0xFFFFCC88);
    }

    private String fit(String text, int maximumWidth) {
        if (MacroScreenBridge.textWidth(screen, text) <= maximumWidth) return text;
        while (!text.isEmpty() && MacroScreenBridge.textWidth(screen, text + "…") > maximumWidth)
            text = text.substring(0, text.length() - 1);
        return text + "…";
    }

    private record Label(String text, int x, int y, int color) {}
}
