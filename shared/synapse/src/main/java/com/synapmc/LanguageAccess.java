package com.synapmc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LanguageAccess {
    private LanguageAccess() { }

    public static Map<String, String> available(Object manager) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        if (manager == null) return result;
        for (Method method : manager.getClass().getMethods()) {
            if (method.getParameterCount() != 0 || !(method.getName().toLowerCase().contains("extension"))) continue;
            try { collect(method.invoke(manager), result); } catch (Exception ignored) { }
        }
        for (Class<?> type = manager.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!field.getName().toLowerCase().contains("extension")) continue;
                try { field.setAccessible(true); collect(field.get(manager), result); } catch (Exception ignored) { }
            }
        }
        return result;
    }

    private static void collect(Object value, Map<String, String> result) {
        if (value instanceof Map<?, ?>) {
            for (Object item : ((Map<?, ?>) value).values()) add(item, result);
        } else if (value instanceof Collection<?>) {
            for (Object item : (Collection<?>) value) add(item, result);
        } else if (value != null && value.getClass().isArray()) {
            for (Object item : (Object[]) value) add(item, result);
        } else add(value, result);
    }

    private static void add(Object extension, Map<String, String> result) {
        if (extension == null) return;
        String language = name(extension);
        String suffix = call(extension, "defaultFileExtension");
        if (language == null || suffix == null || suffix.trim().isEmpty()) return;
        suffix = suffix.trim();
        while (suffix.startsWith(".")) suffix = suffix.substring(1);
        if (!suffix.isEmpty()) result.put(language, suffix);
        addGuestLanguages(extension, result);
    }

    private static void addGuestLanguages(Object extension, Map<String, String> result) {
        if (!"graal".equalsIgnoreCase(name(extension))) return;
        try {
            Field runnerField = extension.getClass().getDeclaredField("runner");
            runnerField.setAccessible(true);
            Object runner = runnerField.get(extension);
            String root = extension.getClass().getName().substring(0, extension.getClass().getName().indexOf(".graal."));
            Class<?> definition = Class.forName(root + ".graal.language.impl.GraalLanguageDefinition", false, extension.getClass().getClassLoader());
            for (Method method : definition.getMethods()) {
                if (!method.getName().equals("getOrCreateEngine") || method.getParameterCount() != 1) continue;
                Object engine = method.invoke(null, runner);
                Map<?, ?> languages = (Map<?, ?>) engine.getClass().getMethod("getLanguages").invoke(engine);
                for (Map.Entry<String, String> guest : Map.of("js", "js", "python", "py", "ruby", "rb", "R", "r").entrySet())
                    if (languages.containsKey(guest.getKey())) result.put(guest.getKey(), guest.getValue());
            }
        } catch (Exception | LinkageError ignored) { }
    }

    public static String forFile(Object extension, java.io.File file) {
        String language = name(extension);
        if (!"graal".equalsIgnoreCase(language)) return language;
        try {
            Class<?> source = Class.forName("org.graalvm.polyglot.Source", false, extension.getClass().getClassLoader());
            String guest = (String) source.getMethod("findLanguage", java.io.File.class).invoke(null, file);
            if (guest != null) return guest;
        } catch (Exception | LinkageError ignored) { }
        return language;
    }

    public static String name(Object extension) {
        if (extension == null) return null;
        String language = call(extension, "getLanguageImplName");
        return language == null ? call(extension, "getExtensionName") : language;
    }

    private static String call(Object target, String methodName) {
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            return value == null ? null : value.toString();
        } catch (Exception ignored) { return null; }
    }
}
