package com.synapmc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class EditorColors {
    private String cachedSource;
    private String[] lines;
    private List<ColorLiteral> colors;
    private boolean renderErrorLogged;

    public void render(Object editor, Object context) {
        try {
            String source = (String) field(field(editor, "history"), "current");
            if (!source.equals(cachedSource)) {
                cachedSource = source;
                lines = source.split("\n", -1);
                colors = ColorLiteral.find(source);
            }
            int first = integer(editor, "firstLine");
            int last = integer(editor, "lastLine");
            int spread = integer(editor, "lineSpread");
            if (spread <= 0) return;
            int offset = (spread - integer(editor, "scroll") % spread) % spread;
            int gutter = 28;
            try {
                gutter = integer(editor, "lineNumWidth");
                offset = -integer(editor, "scroll") % spread;
            } catch (ReflectiveOperationException ignored) { }
            int width = MacroScreenBridge.dimension(editor, "width", "field_22789");
            int height = MacroScreenBridge.dimension(editor, "height", "field_22790");
            int line = 0;
            int lineStart = 0;
            for (ColorLiteral color : colors) {
                while (line < lines.length - 1 && color.start >= lineStart + lines[line].length() + 1) {
                    lineStart += lines[line++].length() + 1;
                }
                if (line < first || line > last) continue;
                int x1 = gutter + 2 + measure(editor, lines[line].substring(0, color.start - lineStart));
                int x2 = gutter + 2 + measure(editor, lines[line].substring(0, color.end - lineStart));
                int y = 13 + offset + (line - first) * spread;
                if (x1 >= width - 11 || y >= height - 13 || y + spread <= 13) continue;
                GuiPaint.fill(context, editor, x1, Math.max(13, y), Math.min(width - 11, x2),
                        Math.min(height - 13, y + spread), highlightColor(color.argb));
            }
        } catch (Exception error) {
            if (!renderErrorLogged) {
                renderErrorLogged = true;
                SynapMcShared.error("[SynapMc] Could not highlight editor colors: " + error);
            }
        }
    }

    private static int highlightColor(int color) {
        // Moderate opacity keeps the color visible without overpowering the code.
        // Preview opacity is independent of ARGB alpha so transparent colors still show.
        return 0x90000000 | color & 0xFFFFFF;
    }

    public static Map<String, Runnable> options(Object editor, int index, Map<String, Runnable> original) {
        Map<String, Runnable> options = new LinkedHashMap<>(original);
        try {
            String source = (String) field(field(editor, "history"), "current");
            ColorLiteral color = ColorLiteral.at(source, index);
            if (color != null) options.put("Color Picker", () -> open(editor, color));
            else if (index >= 0 && index <= source.length()) {
                options.put("Insert Color", () -> openPicker(editor, "0xffffff", value -> {
                    if (!EditorSnippets.replace(editor, source, index, index, value))
                        throw new IllegalStateException("Editor changed. Reopen Insert Color and try again.");
                }));
            }
        } catch (ReflectiveOperationException error) { SynapMcShared.error("[SynapMc] Could not inspect editor color: " + error); }
        return options;
    }

    private static void open(Object editor, ColorLiteral color) {
        openPicker(editor, color.text, value -> replace(editor, color, value));
    }

    private static void openPicker(Object editor, String literal, Consumer<String> apply) {
        try {
            String name = "com.synapmc.ColorPickerScreen";
            if (SynapMcShared.core != null && "legacy".equals(SynapMcShared.core.getJsVersion())) {
                for (Method method : editor.getClass().getMethods()) if (method.getName().equals("method_25394")
                        && method.getParameterCount() == 4 && method.getParameterTypes()[0].getName().equals("net.minecraft.class_332")) {
                    name += "Modern";
                    break;
                }
            }
            Object picker = Class.forName(name).getConstructor(String.class, Consumer.class)
                    .newInstance(literal, apply);
            MacroScreenBridge.showChild(editor, picker);
        } catch (ReflectiveOperationException error) { SynapMcShared.error("[SynapMc] Could not open color picker: " + error); }
    }

    public static boolean replace(Object editor, ColorLiteral color, String value) {
        try {
            Object history = field(editor, "history");
            String current = (String) field(history, "current");
            ColorLiteral replacement = ColorLiteral.at(value, 0);
            if (color.end > current.length() || !current.substring(color.start, color.end).equals(color.text)
                    || replacement == null || replacement.end != value.length()) return false;
            history.getClass().getMethod("replace", int.class, int.class, String.class).invoke(history, color.start, color.end - color.start, value);
            return true;
        } catch (ReflectiveOperationException error) {
            SynapMcShared.error("[SynapMc] Could not replace editor color: " + error);
            return false;
        }
    }

    private static int measure(Object editor, String value) throws ReflectiveOperationException {
        Object font;
        try { font = field(editor, "font"); } catch (NoSuchFieldException error) { font = field(editor, "field_22793"); }
        Object component;
        try { component = Class.forName("net.minecraft.network.chat.Component").getMethod("literal", String.class).invoke(null, value); }
        catch (ClassNotFoundException error) { component = Class.forName("net.minecraft.class_2561").getMethod("method_43470", String.class).invoke(null, value); }
        Object style = field(editor, "defaultStyle");
        for (Method method : component.getClass().getMethods()) if ((method.getName().equals("setStyle") || method.getName().equals("method_10862"))
                && method.getParameterCount() == 1 && method.getParameterTypes()[0].isInstance(style)) { method.invoke(component, style); break; }
        for (Method method : font.getClass().getMethods()) if ((method.getName().equals("width") || method.getName().equals("method_27525"))
                && method.getParameterCount() == 1 && method.getParameterTypes()[0].isInstance(component))
            return ((Number) method.invoke(font, component)).intValue();
        return MacroScreenBridge.textWidth(editor, value);
    }

    private static int integer(Object owner, String name) throws ReflectiveOperationException { return ((Number) field(owner, name)).intValue(); }
    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(owner); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
}
