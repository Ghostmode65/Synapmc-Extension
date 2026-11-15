package com.synapmc;

import java.awt.Color;
import java.util.function.Consumer;
import java.util.List;
import com.synapmc.library.FSynapse;

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
    private Object addFavoriteButton;
    private Runnable pendingAction;
    private Object favoritePrevious, favoriteNext, recentPrevious, recentNext;
    private int paletteLeft, paletteWidth, paletteColumns, paletteRows, cellSize;
    private int favoritesTop, recentTop, favoritePage, recentPage;
    private int settingsLeft, settingsWidth, alphaY, brightnessY;
    private int highlightAlpha, highlightBrightness;

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
        gridWidth = Math.max(48, Math.min(180, width - 196));
        gridHeight = Math.max(24, Math.min(120, height - 140));
        left = Math.max(8, (width - gridWidth - 24) / 2);
        top = Math.max(40, (height - gridHeight - 100) / 2);
        paletteLeft = 12;
        paletteWidth = Math.max(32, left - paletteLeft - 12);
        cellSize = 18;
        paletteColumns = Math.max(1, paletteWidth / cellSize);
        paletteRows = Math.max(1, Math.min(3, (height - 160) / 36));
        favoritesTop = top;
        recentTop = Math.max(top + paletteRows * cellSize + 68, height - paletteRows * cellSize - 48);
        settingsLeft = left + gridWidth + 38;
        settingsWidth = Math.max(32, width - settingsLeft - 12);
        alphaY = top + 30;
        brightnessY = top + 86;
        highlightAlpha = SynapMcShared.config == null ? 144 : SynapMcShared.config.highlightAlpha;
        highlightBrightness = SynapMcShared.config == null ? 100 : SynapMcShared.config.highlightBrightness;
        try {
            // Native TextInput places its nine-pixel text two pixels below the top.
            hexInput = MacroScreenBridge.addTextInput(screen, left + 32, top + gridHeight + 26,
                    gridWidth - 8, 13, this::readHex);
            updateHex();
            MacroScreenBridge.addButton(screen, left, top + gridHeight + 64, gridWidth / 2 - 2, 20, "Apply", this::apply);
            MacroScreenBridge.addButton(screen, left + gridWidth / 2 + 2, top + gridHeight + 64,
                    gridWidth / 2 + 22, 20, "Cancel", () -> MacroScreenBridge.back(screen));
            addFavoriteButton = MacroScreenBridge.addButton(screen, paletteLeft, favoritesTop,
                    cellSize - 2, cellSize - 2, "+", this::addFavorite);
            MacroScreenBridge.setTooltip(addFavoriteButton, "Add current color to favorites");
            paletteNavigation(favoritesTop + paletteRows * cellSize + 4, true);
            paletteNavigation(recentTop + paletteRows * cellSize + 4, false);
            updatePaletteNavigation();
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

    private void addFavorite() {
        readHex(MacroScreenBridge.inputValue(hexInput));
        if (!error.isEmpty()) return;
        if (!EditorPalette.addFavorite(color())) error = "Could not save favorite (limit 48).";
        else favoritePage = Math.max(0, (EditorPalette.favorites().size() - 1) / (paletteColumns * paletteRows));
        updatePaletteNavigation();
    }

    private void paletteNavigation(int y, boolean favorites) throws Exception {
        int buttonWidth = Math.min(28, (paletteWidth - 4) / 2);
        Object previous = MacroScreenBridge.addButton(screen, paletteLeft, y, buttonWidth, 14, "<", () -> {
            if (favorites) favoritePage = Math.max(0, favoritePage - 1);
            else recentPage = Math.max(0, recentPage - 1);
            updatePaletteNavigation();
        });
        Object next = MacroScreenBridge.addButton(screen, paletteLeft + paletteWidth - buttonWidth, y, buttonWidth, 14, ">", () -> {
            int size = favorites ? favoriteSlots() : EditorPalette.recent().size();
            int page = favorites ? favoritePage : recentPage;
            if ((page + 1) * paletteColumns * paletteRows < size) {
                if (favorites) favoritePage++; else recentPage++;
            }
            updatePaletteNavigation();
        });
        if (favorites) { favoritePrevious = previous; favoriteNext = next; }
        else { recentPrevious = previous; recentNext = next; }
    }

    private void updatePaletteNavigation() {
        int pageSize = paletteColumns * paletteRows;
        favoritePage = Math.min(favoritePage, Math.max(0, (favoriteSlots() - 1) / pageSize));
        recentPage = Math.min(recentPage, Math.max(0, (EditorPalette.recent().size() - 1) / pageSize));
        int count = EditorPalette.favorites().size();
        int addIndex = count - favoritePage * pageSize;
        boolean showAdd = count < 48 && addIndex >= 0 && addIndex < pageSize;
        MacroScreenBridge.visible(addFavoriteButton, showAdd);
        if (showAdd) MacroScreenBridge.position(addFavoriteButton, paletteLeft + addIndex % paletteColumns * cellSize,
                favoritesTop + addIndex / paletteColumns * cellSize, cellSize - 2, cellSize - 2);
        MacroScreenBridge.visible(favoritePrevious, favoriteSlots() > pageSize);
        MacroScreenBridge.visible(favoriteNext, favoriteSlots() > pageSize);
        MacroScreenBridge.visible(recentPrevious, EditorPalette.recent().size() > pageSize);
        MacroScreenBridge.visible(recentNext, EditorPalette.recent().size() > pageSize);
    }

    private static int favoriteSlots() {
        int count = EditorPalette.favorites().size();
        return count + (count < 48 ? 1 : 0);
    }

    private int color() { return alpha << 24 | Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF; }
    private void updateHex() {
        error = ""; MacroScreenBridge.inputText(hexInput, original.format(color()));
    }
    private void apply() {
        readHex(MacroScreenBridge.inputValue(hexInput));
        if (!error.isEmpty()) return;
        try { apply.accept(original.format(color())); }
        catch (IllegalStateException failure) { error = failure.getMessage(); return; }
        EditorPalette.remember(color());
        MacroScreenBridge.back(screen);
    }

    public boolean keyPressed(int key) {
        if (MacroScreenBridge.hasOverlay(screen)) return false;
        if (key == 257 || key == 335) { apply(); return true; }
        return false;
    }

    public boolean click(double x, double y, int button) {
        if (MacroScreenBridge.hasOverlay(screen) || button != 0 && button != 1) return false;
        if (selectPalette(x, y, EditorPalette.favorites(), favoritesTop, favoritePage, button, true)
                || selectPalette(x, y, EditorPalette.recent(), recentTop, recentPage, button, false)) return true;
        if (button != 0) return false;
        if (x >= left && x < left + gridWidth && y >= top && y < top + gridHeight) dragging = 1;
        else if (x >= left + gridWidth + 8 && x < left + gridWidth + 24 && y >= top && y < top + gridHeight) dragging = 2;
        else if (original.text.length() == 10 && x >= left && x < left + gridWidth
                && y >= top + gridHeight + 8 && y < top + gridHeight + 18) dragging = 3;
        else if (x >= settingsLeft && x < settingsLeft + settingsWidth && y >= alphaY && y < alphaY + 12) dragging = 4;
        else if (x >= settingsLeft && x < settingsLeft + settingsWidth && y >= brightnessY && y < brightnessY + 12) dragging = 5;
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
        else if (dragging == 3) alpha = Math.round(clamp((x - left) / (gridWidth - 1)) * 255);
        else if (dragging == 4) highlightAlpha = Math.round(clamp((x - settingsLeft) / (settingsWidth - 1)) * 255);
        else highlightBrightness = Math.round(clamp((x - settingsLeft) / (settingsWidth - 1)) * 100);
        if (dragging <= 3) updateHex();
        return true;
    }
    public void release() {
        if (dragging == 4 || dragging == 5) {
            boolean alphaSetting = dragging == 4;
            if (!new FSynapse(null, null).saveConfig(alphaSetting ? "highlightAlpha" : "highlightBrightness",
                    alphaSetting ? highlightAlpha : highlightBrightness)) {
                error = "Could not save editor settings.";
                highlightAlpha = SynapMcShared.config == null ? 144 : SynapMcShared.config.highlightAlpha;
                highlightBrightness = SynapMcShared.config == null ? 100 : SynapMcShared.config.highlightBrightness;
            }
        }
        dragging = 0;
    }

    private boolean selectPalette(double x, double y, List<Integer> colors, int paletteTop, int page, int button, boolean favorite) {
        if (x < paletteLeft || x >= paletteLeft + paletteColumns * cellSize
                || y < paletteTop || y >= paletteTop + paletteRows * cellSize) return false;
        int column = (int) (x - paletteLeft) / cellSize;
        int row = (int) (y - paletteTop) / cellSize;
        int index = page * paletteColumns * paletteRows + row * paletteColumns + column;
        if (index >= colors.size()) return false;
        int selected = colors.get(index);
        if (button == 1) {
            if (favorite) {
                if (!EditorPalette.remove(selected, true)) error = "Could not delete saved color.";
                updatePaletteNavigation();
                return true;
            }
            try {
                MacroScreenBridge.choiceMenu(screen, (int) x, (int) y, List.of("Delete"), ignored -> {
                    pendingAction = () -> {
                        if (!EditorPalette.remove(selected, favorite)) error = "Could not delete saved color.";
                        updatePaletteNavigation();
                    };
                });
            } catch (Exception failure) { error = "Could not open color menu."; }
            return true;
        }
        setColor(original.text.length() == 8 ? selected | 0xFF000000 : selected);
        updateHex();
        MacroScreenBridge.blurInput(hexInput);
        apply();
        return true;
    }
    private static float clamp(double value) { return (float) Math.max(0, Math.min(1, value)); }

    public void render(Object context) {
        if (pendingAction != null && !MacroScreenBridge.hasOverlay(screen)) {
            Runnable action = pendingAction; pendingAction = null; action.run();
        }
        if (context == null || MacroScreenBridge.hasOverlay(screen)) return;
        updatePaletteNavigation();
        palette(context, "Favorites", EditorPalette.favorites(), favoritesTop, favoritePage);
        palette(context, "Recent", EditorPalette.recent(), recentTop, recentPage);
        MacroScreenBridge.drawText(context, screen, "Editor", settingsLeft, top - 16, 0xFFFFFFFF);
        setting(context, "Alpha", alphaY, highlightAlpha / 255f);
        setting(context, "Brightness", brightnessY, highlightBrightness / 100f);
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

    private void palette(Object context, String title, List<Integer> colors, int paletteTop, int page) {
        MacroScreenBridge.drawText(context, screen, title, paletteLeft, paletteTop - 16, 0xFFFFFFFF);
        int pageSize = paletteColumns * paletteRows;
        for (int i = 0; i < pageSize && page * pageSize + i < colors.size(); i++) {
            int x = paletteLeft + i % paletteColumns * cellSize;
            int y = paletteTop + i / paletteColumns * cellSize;
            int selected = colors.get(page * pageSize + i);
            GuiPaint.fill(context, screen, x, y, x + cellSize - 2, y + cellSize - 2,
                    selected == color() ? 0xFFFFFFFF : 0xFF555555);
            checker(context, x + 1, y + 1, cellSize - 4, cellSize - 4);
            GuiPaint.fill(context, screen, x + 1, y + 1, x + cellSize - 3, y + cellSize - 3, selected);
        }
    }

    private void setting(Object context, String title, int y, float amount) {
        boolean opacity = title.equals("Alpha");
        if (opacity) MacroScreenBridge.drawText(context, screen, "Highlight", settingsLeft, y - 26, 0xFFBBBBBB);
        MacroScreenBridge.drawText(context, screen, title, settingsLeft, y - 14, 0xFFFFFFFF);
        checker(context, settingsLeft, y, settingsWidth, 12);
        for (int x = 0; x < settingsWidth; x += 2) {
            float position = (float) x / (settingsWidth - 1);
            int previewAlpha = opacity ? Math.round(position * 255) : highlightAlpha;
            int previewBrightness = opacity ? highlightBrightness : Math.round(position * 100);
            GuiPaint.fill(context, screen, settingsLeft + x, y, settingsLeft + Math.min(settingsWidth, x + 2), y + 12,
                    EditorColors.highlightColor(color(), previewAlpha, previewBrightness));
        }
        int selected = settingsLeft + Math.round((settingsWidth - 1) * amount);
        GuiPaint.fill(context, screen, selected - 1, y - 1, selected + 2, y + 13, 0xFF000000);
        GuiPaint.fill(context, screen, selected, y, selected + 1, y + 12, 0xFFFFFFFF);
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
