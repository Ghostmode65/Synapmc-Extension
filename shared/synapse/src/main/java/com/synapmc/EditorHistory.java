package com.synapmc;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class EditorHistory {
    private EditorHistory() { }

    public static void attach(Object editor) {
        try {
            Object save = field(editor, "saveBtn");
            int x = coordinate(save, "getX", "method_46426", "x", "field_22760");
            int y = coordinate(save, "getY", "method_46427", "y", "field_22761");
            int width = coordinate(save, "getWidth", "method_25368", "width", "field_22758");
            int height = coordinate(save, "getHeight", "method_25364", "height", "field_22759");
            Object button = MacroScreenBridge.addButton(editor, Math.max(0, x - width), y, width, height,
                    "History", () -> open(editor));
            MacroScreenBridge.setTooltip(button, "Browse saved script versions");
            capture(editor);
        } catch (Exception error) { SynapMcShared.error("[SynapMc] Could not attach History: " + error); }
    }

    /** Used before and after the native save, preserving the overwritten disk version. */
    public static void capture(Object editor) {
        try { ScriptHistoryService.capture((File) field(editor, "file")); }
        catch (Exception error) { SynapMcShared.error("[SynapMc] Could not inspect script history: " + error); }
    }

    public static void open(Object editor) {
        try {
            String name = "com.synapmc.ScriptHistoryScreen";
            if (SynapMcShared.core != null && "legacy".equals(SynapMcShared.core.getJsVersion()))
                for (Method method : editor.getClass().getMethods())
                    if (method.getName().equals("method_25394") && method.getParameterCount() == 4
                            && method.getParameterTypes()[0].getName().equals("net.minecraft.class_332")) { name += "Modern"; break; }
            Object screen = Class.forName(name).getConstructor(Object.class).newInstance(editor);
            MacroScreenBridge.showChild(editor, screen);
        } catch (Exception error) { SynapMcShared.error("[SynapMc] Could not open History: " + error); }
    }

    /** One native undoable edit, with a guard against overwriting changes made while browsing. */
    public static boolean restore(Object editor, String expected, String replacement) {
        try {
            Object history = field(editor, "history");
            if (!expected.equals(field(history, "current"))) return false;
            if (!expected.equals(replacement)) history.getClass().getMethod("replace", int.class, int.class, String.class)
                    .invoke(history, 0, expected.length(), replacement);
            Object cursor = field(editor, "cursor");
            cursor.getClass().getMethod("updateStartIndex", int.class, String.class).invoke(cursor, 0, replacement);
            cursor.getClass().getMethod("updateEndIndex", int.class, String.class).invoke(cursor, 0, replacement);
            setOptional(cursor, "dragStartIndex", 0);
            setOptional(cursor, "arrowLineIndex", 0);
            setOptional(cursor, "arrowEnd", false);
            try { editor.getClass().getMethod("scrollToCursor").invoke(editor); }
            catch (NoSuchMethodException ignored) { }
            return true;
        } catch (Exception error) { SynapMcShared.error("[SynapMc] Could not restore History: " + error); return false; }
    }

    private static void setOptional(Object owner, String name, Object value) throws IllegalAccessException {
        try { owner.getClass().getField(name).set(owner, value); }
        catch (NoSuchFieldException ignored) { }
    }

    private static int coordinate(Object owner, String named, String mapped, String field, String mappedField) throws Exception {
        for (String method : new String[] {named, mapped})
            try { return ((Number) owner.getClass().getMethod(method).invoke(owner)).intValue(); }
            catch (NoSuchMethodException ignored) { }
        try { return ((Number) field(owner, field)).intValue(); }
        catch (NoSuchFieldException ignored) { return ((Number) field(owner, mappedField)).intValue(); }
    }

    static Object field(Object owner, String name) throws ReflectiveOperationException {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(owner); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
}
