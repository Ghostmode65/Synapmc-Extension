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
        String language = call(extension, "getLanguageImplName");
        if (language == null) language = call(extension, "getExtensionName");
        String suffix = call(extension, "defaultFileExtension");
        if (language == null || suffix == null || suffix.trim().isEmpty()) return;
        suffix = suffix.trim();
        while (suffix.startsWith(".")) suffix = suffix.substring(1);
        if (!suffix.isEmpty()) result.put(language, suffix);
    }

    private static String call(Object target, String methodName) {
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            return value == null ? null : value.toString();
        } catch (Exception ignored) { return null; }
    }
}
