package com.synapmc;

import java.awt.Color;
import java.util.function.Consumer;

public final class ColorPickerUi {
    private final Object screen;
    private final ColorLiteral original;
    private final Consumer<String> apply;
    private Object hexInput;
    private float hue;
    private float saturation;
    private float brightness;
    private int alpha;
    private int left, top, gridWidth, gridHeight;
    private int dragging;
    private String error = "";

    public ColorPickerUi(Object screen, String literal, Consumer<String> apply) {
        this.screen = screen;
        this.original = ColorLiteral.at(literal, 0);
        if (original == null) throw new IllegalArgumentException("Invalid color literal");
        this.apply = apply;
        setColor(original.argb);
    }

    public void init() {
        int width = MacroScreenBridge.dimension(screen, "width", "field_22789");
        int height = MacroScreenBridge.dimension(screen, "height", "field_22790");
        gridWidth = Math.max(32, Math.min(180, width - 64));
        gridHeight = Math.max(24, Math.min(120, height - 140));
        left = Math.max(8, (width - gridWidth - 24) / 2);
        top = Math.max(24, (height - gridHeight - 100) / 2);
        try {
            // Native TextInput places its nine-pixel text two pixels below the top.
            hexInput = MacroScreenBridge.addTextInput(screen, left + 32, top + gridHeight + 26,
                    gridWidth - 8, 13, this::readHex);
            updateHex();
            MacroScreenBridge.addButton(screen, left, top + gridHeight + 64, gridWidth / 2 - 2, 20, "Apply", this::apply);
            MacroScreenBridge.addButton(screen, left + gridWidth / 2 + 2, top + gridHeight + 64,
                    gridWidth / 2 + 22, 20, "Cancel", () -> MacroScreenBridge.back(screen));
        } catch (Exception error) { SynapMcShared.error("[SynapMc] Could not initialize color picker: " + error); }
    }

    private void readHex(String value) {
        ColorLiteral color = ColorLiteral.at(value, 0);
        if (color == null || color.end != value.length() || color.text.length() != original.text.length()) {
            error = original.text.length() == 8 ? "Use 0x plus six hex digits" : "Use 0x plus eight hex digits (ARGB)";
            return;
        }
        setColor(color.argb);
        error = "";
    }

    private void setColor(int color) {
        float[] hsv = Color.RGBtoHSB(color >> 16 & 255, color >> 8 & 255, color & 255, null);
        hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]; alpha = color >>> 24;
    }

    private int color() { return alpha << 24 | Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF; }
    private void updateHex() { error = ""; MacroScreenBridge.inputText(hexInput, original.format(color())); }
    private void apply() {
        readHex(MacroScreenBridge.inputValue(hexInput));
        if (!error.isEmpty()) return;
        try { apply.accept(original.format(color())); }
        catch (IllegalStateException failure) { error = failure.getMessage(); return; }
        MacroScreenBridge.back(screen);
    }

    public boolean keyPressed(int key) {
        if (key == 257 || key == 335) { apply(); return true; }
        return false;
    }

    public boolean click(double x, double y, int button) {
        if (button != 0) return false;
        if (x >= left && x < left + gridWidth && y >= top && y < top + gridHeight) dragging = 1;
        else if (x >= left + gridWidth + 8 && x < left + gridWidth + 24 && y >= top && y < top + gridHeight) dragging = 2;
        else if (original.text.length() == 10 && x >= left && x < left + gridWidth
                && y >= top + gridHeight + 8 && y < top + gridHeight + 18) dragging = 3;
        else return false;
        MacroScreenBridge.blurInput(hexInput);
        drag(x, y);
        return true;
    }

    public boolean drag(double x, double y) {
        if (dragging == 0) return false;
        if (dragging == 1) {
            saturation = clamp((x - left) / (gridWidth - 1));
            brightness = 1 - clamp((y - top) / (gridHeight - 1));
        } else if (dragging == 2) hue = clamp((y - top) / (gridHeight - 1));
        else alpha = Math.round(clamp((x - left) / (gridWidth - 1)) * 255);
        updateHex();
        return true;
    }
    public void release() { dragging = 0; }
    private static float clamp(double value) { return (float) Math.max(0, Math.min(1, value)); }

    public void render(Object context) {
        MacroScreenBridge.drawText(context, screen, "Color Picker", left, top - 16, 0xFFFFFFFF);
        for (int y = 0; y < gridHeight; y += 4) for (int x = 0; x < gridWidth; x += 4)
            GuiPaint.fill(context, screen, left + x, top + y, left + Math.min(gridWidth, x + 4), top + Math.min(gridHeight, y + 4),
                    Color.HSBtoRGB(hue, (float) x / (gridWidth - 1), 1 - (float) y / (gridHeight - 1)));
        for (int y = 0; y < gridHeight; y += 2)
            GuiPaint.fill(context, screen, left + gridWidth + 8, top + y, left + gridWidth + 24,
                    top + Math.min(gridHeight, y + 2), Color.HSBtoRGB((float) y / (gridHeight - 1), 1, 1));
        if (original.text.length() == 10) {
            checker(context, left, top + gridHeight + 8, gridWidth, 10);
            for (int x = 0; x < gridWidth; x += 4)
                GuiPaint.fill(context, screen, left + x, top + gridHeight + 8, left + Math.min(gridWidth, x + 4), top + gridHeight + 18,
                        Math.round(255f * x / (gridWidth - 1)) << 24 | color() & 0xFFFFFF);
            marker(context, left + Math.round(alpha / 255f * (gridWidth - 1)), top + gridHeight + 13);
        }
        checker(context, left, top + gridHeight + 24, 24, 18);
        GuiPaint.fill(context, screen, left, top + gridHeight + 24, left + 24, top + gridHeight + 42, color());
        marker(context, left + Math.round(saturation * (gridWidth - 1)), top + Math.round((1 - brightness) * (gridHeight - 1)));
        marker(context, left + gridWidth + 16, top + Math.round(hue * (gridHeight - 1)));
        if (!error.isEmpty()) MacroScreenBridge.drawText(context, screen, error, left, top + gridHeight + 48, 0xFFFF7777);
    }
    private void marker(Object context, int x, int y) {
        GuiPaint.fill(context, screen, x - 3, y - 3, x + 4, y + 4, 0xFF000000);
        GuiPaint.fill(context, screen, x - 2, y - 2, x + 3, y + 3, 0xFFFFFFFF);
        GuiPaint.fill(context, screen, x - 1, y - 1, x + 2, y + 2, 0xFF000000);
    }
    private void checker(Object context, int x, int y, int width, int height) {
        for (int row = 0; row < height; row += 6) for (int col = 0; col < width; col += 6)
            GuiPaint.fill(context, screen, x + col, y + row, x + Math.min(width, col + 6), y + Math.min(height, row + 6),
                    ((row / 6 + col / 6) % 2 == 0) ? 0xFFBBBBBB : 0xFF777777);
    }
}
