package com.synapmc;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class EditorSnippets {
    private EditorSnippets() {}

    public static List<SmartSnippet.Completion> completions(Object editor) {
        if (SynapMcShared.roamingDir == null) return List.of();
        try {
            Object value = field(editor, "file");
            if (!(value instanceof File file)) return List.of();
            var root = SynapMcShared.roamingDir.toPath().resolve("scripts/snippets").toRealPath();
            var path = file.toPath().toRealPath();
            if (path.equals(root) || !path.startsWith(root)) return List.of();
            Object cursor = field(editor, "cursor");
            int start = (int) field(cursor, "startIndex");
            if (start != (int) field(cursor, "endIndex")) return List.of();
            String source = (String) field(field(editor, "history"), "current");
            return SmartSnippet.completions(source, start);
        } catch (IOException ignored) {
            return List.of();
        } catch (ReflectiveOperationException error) {
            SynapMcShared.error("[SynapMc] Could not inspect snippet completion: " + error);
            return List.of();
        }
    }

    public static Map<String, Runnable> options(Object editor, int index, Map<String, Runnable> original) {
        Map<String, Runnable> options = new LinkedHashMap<>(original);
        options.put("snippets", () -> openForEditor(editor, index));
        return options;
    }

    private static void openForEditor(Object editor, int index) {
        try {
            String source = (String) field(field(editor, "history"), "current");
            Object cursor = field(editor, "cursor");
            int start = Math.min((int) field(cursor, "startIndex"), (int) field(cursor, "endIndex"));
            int end = Math.max((int) field(cursor, "startIndex"), (int) field(cursor, "endIndex"));
            if (index < start || index > end || start == end) start = end = Math.max(0, Math.min(source.length(), index));
            int selectionStart = start, selectionEnd = end;
            File file = (File) field(editor, "file");
            open(editor, SnippetStore.extension(file.getName()), code -> {
                if (!replace(editor, source, selectionStart, selectionEnd, code))
                    throw new IllegalStateException("Editor changed. Reopen Snippets and try again.");
            });
        } catch (ReflectiveOperationException error) {
            SynapMcShared.error("[SynapMc] Could not inspect editor for snippets: " + error);
        }
    }

    public static void open(Object parent, String extension, Consumer<String> insert) {
        try {
            String name = "com.synapmc.SnippetScreen";
            if (SynapMcShared.core != null && "legacy".equals(SynapMcShared.core.getJsVersion())) {
                for (Method method : parent.getClass().getMethods()) if (method.getName().equals("method_25394")
                        && method.getParameterCount() == 4 && method.getParameterTypes()[0].getName().equals("net.minecraft.class_332")) {
                    name += "Modern";
                    break;
                }
            }
            Object screen = Class.forName(name).getConstructor(String.class, Consumer.class).newInstance(extension, insert);
            MacroScreenBridge.showChild(parent, screen);
        } catch (ReflectiveOperationException error) {
            SynapMcShared.error("[SynapMc] Could not open snippets: " + error);
        }
    }

    public static boolean replace(Object editor, String expected, int start, int end, String code) {
        try {
            Object history = field(editor, "history");
            String source = (String) field(history, "current");
            if (!source.equals(expected)) return false;
            SmartSnippet.Insertion insertion = SmartSnippet.insert(source, start, end, code);
            history.getClass().getMethod("replace", int.class, int.class, String.class)
                    .invoke(history, insertion.start(), insertion.length(), insertion.text());
            String current = (String) field(history, "current");
            Object cursor = field(editor, "cursor");
            cursor.getClass().getMethod("updateStartIndex", int.class, String.class).invoke(cursor, insertion.cursor(), current);
            cursor.getClass().getMethod("updateEndIndex", int.class, String.class).invoke(cursor, insertion.cursor(), current);
            optionalSet(cursor, "dragStartIndex", insertion.cursor());
            optionalSet(cursor, "arrowLineIndex", field(cursor, "endLineIndex"));
            optionalSet(cursor, "arrowEnd", false);
            try { editor.getClass().getMethod("scrollToCursor").invoke(editor); }
            catch (NoSuchMethodException ignored) { }
            return true;
        } catch (ReflectiveOperationException | IllegalArgumentException error) {
            SynapMcShared.error("[SynapMc] Could not insert snippet: " + error);
            return false;
        }
    }

    private static void optionalSet(Object owner, String name, Object value) throws IllegalAccessException {
        try { owner.getClass().getField(name).set(owner, value); }
        catch (NoSuchFieldException ignored) { }
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(owner); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
}
