package com.synapmc;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class TrustFolderUi {
    private final Object screen;
    private final Path folder;
    private final Runnable onTrust;
    private final Runnable onCancel;
    private String error;
    private boolean handled;
    private final List<Line> text = new ArrayList<Line>();

    public TrustFolderUi(Object screen, Path folder, Runnable onTrust, Runnable onCancel) {
        this.screen = screen;
        this.folder = folder;
        this.onTrust = onTrust;
        this.onCancel = onCancel;
    }

    public void init() {
        text.clear();
        int width = MacroScreenBridge.dimension(screen, "width", "field_22789");
        int height = MacroScreenBridge.dimension(screen, "height", "field_22790");
        if (width == 0 || height == 0) return;
        try {
            line("Trust this folder to execute its scripts?", 8, 28, 0xFFFFFFFF);
            String path = folder.toString();
            int charsPerLine = Math.max(20, (width - 16) / 6);
            for (int offset = 0, row = 0; offset < path.length(); offset += charsPerLine, row++) {
                line(path.substring(offset, Math.min(path.length(), offset + charsPerLine)),
                        8, 48 + row * 12, 0xFFBBBBBB);
            }
            if (error != null) line(error, 8, height - 55, 0xFFFF7777);
            MacroScreenBridge.addButton(screen, 8, height - 28, Math.max(80, (width - 24) / 2), 20,
                    "Trust folder and execute", this::trust);
            MacroScreenBridge.addButton(screen, width / 2 + 4, height - 28, Math.max(60, width / 2 - 12), 20,
                    "Cancel", this::cancel);
        } catch (Exception failure) {
            SynapMcShared.error("[SynapMc] Could not show folder trust prompt: " + failure);
        }
    }

    private void trust() {
        try {
            TrustedScriptFolders.trust(folder);
            handled = true;
            MacroScreenBridge.back(screen);
            MacroScreenBridge.onClient(onTrust);
        } catch (IOException failure) {
            error = "Could not save trusted folder. See log.";
            SynapMcShared.error("[SynapMc] Could not save trusted folder: " + failure);
            line(error, 8,
                    MacroScreenBridge.dimension(screen, "height", "field_22790") - 55, 0xFFFF7777);
        }
    }

    private void cancel() {
        handled = true;
        MacroScreenBridge.back(screen);
        MacroScreenBridge.onClient(onCancel);
    }

    public void onRemoved() {
        if (!handled) {
            handled = true;
            MacroScreenBridge.onClient(onCancel);
        }
    }

    public void render(Object context) {
        for (Line line : text) MacroScreenBridge.drawText(context, screen, line.value, line.x, line.y, line.color);
    }

    private void line(String value, int x, int y, int color) {
        text.add(new Line(value, x, y, color));
    }

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
}
