package com.synapmc;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Compact native error components while retaining their complete text and editor links. */
public final class MiniLogs {
    private MiniLogs() {}

    public static Object compact(Object error, Object original) {
        SynapMcConfig config = SynapMcShared.config;
        if (config == null || error == null || original == null) return original;
        try {
            String fullText = (String) invoke(original, new String[] {"getString", "method_10851"});
            int lines = fullText.split("\\r\\n|\\r|\\n", -1).length;
            if (config.miniLogs > 0 && lines < config.miniLogs) return original;

            File file = null;
            int line = 0;
            Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Object frame = error; frame != null && visited.add(frame); frame = field(frame, "next")) {
                Object location = field(frame, "location");
                if (location == null) continue;
                Object source = field(location, "file");
                if (!(source instanceof File)) continue;
                file = ((File) source).getAbsoluteFile();
                Object sourceLine = field(location, "line");
                if (sourceLine instanceof Number) line = Math.max(0, ((Number) sourceLine).intValue());
                break;
            }
            String namespace = error.getClass().getName().split("\\.core\\.", 2)[0];
            Class<?> text = textType();
            Object result = literal(text, file == null ? "Unknown script" : file.getName());
            File script = file;
            int sourceLine = line;
            append(result, action(text, namespace, "  <Open File>", script == null ? null
                    : () -> MinecraftAccess.openFile(script)));
            append(result, literal(text, "\nLines: " + line));
            append(result, action(text, namespace, "  <Open Editor>", script == null ? null
                    : () -> MinecraftAccess.openEditor(script, Math.max(1, sourceLine))));
            append(result, literal(text, "\n" + (file == null ? "Unknown filepath" : file.getParent())));
            append(result, action(text, namespace, "  <Show Error>", () -> MinecraftAccess.showChatComponent(original)));
            append(result, literal(text, "\n"));
            append(result, action(text, namespace, "  <Copy Error>", () -> MinecraftAccess.copyToClipboard(fullText)));
            return result;
        } catch (Exception failure) {
            // Keep native output if a runtime uses an unsupported text or click API.
            System.err.println("[SynapMc] Could not compact error log: " + failure);
            return original;
        }
    }

    private static Class<?> textType() throws ClassNotFoundException {
        try { return Class.forName("net.minecraft.network.chat.Component"); }
        catch (ClassNotFoundException ignored) { return Class.forName("net.minecraft.class_2561"); }
    }

    private static Object literal(Class<?> type, String value) throws Exception {
        return invoke(type, new String[] {"literal", "method_43470"}, value);
    }

    private static void append(Object owner, Object text) throws Exception {
        invoke(owner, new String[] {"append", "method_10852"}, text);
    }

    private static Object action(Class<?> text, String namespace, String caption, Runnable callback) throws Exception {
        Object component = literal(text, caption);
        Class<?> styleType = Class.forName(text.getName().equals("net.minecraft.class_2561")
                ? "net.minecraft.class_2583" : "net.minecraft.network.chat.Style");
        Object style = null;
        for (var field : styleType.getFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) && field.getType() == styleType) {
                style = field.get(null);
                break;
            }
        }
        if (style == null) throw new NoSuchFieldException("Empty text style");
        style = invoke(style, new String[] {"withColor", "method_36139"}, callback == null ? 0x888888 : 0xFFAA00);
        if (callback != null) {
            Class<?> clickType;
            try { clickType = Class.forName(namespace + ".access.CustomClickEvent"); }
            catch (ClassNotFoundException ignored) { clickType = Class.forName(namespace + ".client.access.CustomClickEvent"); }
            Object click = clickType.getConstructor(Runnable.class).newInstance(callback);
            style = invoke(style, new String[] {"withClickEvent", "method_10958"}, click);
            style = invoke(style, new String[] {"withUnderlined", "method_30938"}, true);
        }
        return invoke(component, new String[] {"setStyle", "method_10862"}, style);
    }

    private static Object field(Object owner, String name) throws IllegalAccessException {
        try { return owner.getClass().getField(name).get(owner); }
        catch (NoSuchFieldException ignored) { return null; }
    }

    private static Object invoke(Object owner, String[] names, Object... arguments) throws Exception {
        Class<?> type = owner instanceof Class<?> ? (Class<?>) owner : owner.getClass();
        for (String name : names) for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) continue;
            Class<?>[] parameters = method.getParameterTypes();
            boolean compatible = true;
            for (int i = 0; i < arguments.length; i++) {
                if (!parameters[i].isInstance(arguments[i]) && !(parameters[i] == int.class && arguments[i] instanceof Integer)
                        && !(parameters[i] == boolean.class && arguments[i] instanceof Boolean)) compatible = false;
            }
            if (compatible) return method.invoke(owner instanceof Class<?> ? null : owner, arguments);
        }
        throw new NoSuchMethodException(type.getName() + "." + names[0]);
    }
}
