package com.synapmc;

/** Native button icons drawn using the same GUI fill API on every edition. */
final class HeaderIcon {
    private HeaderIcon() { }

    // Compact pixel glyph with no change to the button's hitbox.
    private static final String[] REFRESH = {
            "   #####   ",
            "  #######  ",
            " ##     ## ",
            " ##     ## ",
            "##      ###",
            "##       ##",
            "##        #",
            "##         ",
            " ##     ## ",
            "  #######  ",
            "   #####   "
    };

    static void drawRefresh(Object context, Object button, int color) {
        int width = MacroScreenBridge.dimension(button, "width", "field_22758");
        int height = MacroScreenBridge.dimension(button, "height", "field_22759");
        int x = coordinate(button, "getX", "method_46426", "x", "field_22760") + (width - REFRESH[0].length()) / 2;
        int y = coordinate(button, "getY", "method_46427", "y", "field_22761") + (height - REFRESH.length) / 2;
        for (int row = 0; row < REFRESH.length; row++) {
            String line = REFRESH[row];
            for (int col = 0; col < line.length();) {
                if (line.charAt(col) != '#') { col++; continue; }
                int end = col + 1;
                while (end < line.length() && line.charAt(end) == '#') end++;
                GuiPaint.fill(context, button, x + col, y + row, x + end, y + row + 1, color);
                col = end;
            }
        }
    }

    private static int coordinate(Object button, String named, String intermediary, String field, String oldField) {
        for (String method : new String[] {named, intermediary}) {
            try { return ((Number) button.getClass().getMethod(method).invoke(button)).intValue(); }
            catch (ReflectiveOperationException ignored) { }
        }
        return MacroScreenBridge.dimension(button, field, oldField);
    }

}
