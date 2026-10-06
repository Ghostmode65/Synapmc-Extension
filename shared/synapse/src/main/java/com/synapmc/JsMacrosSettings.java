package com.synapmc;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JsMacrosSettings {
    private static final Gson GSON = new Gson();

    private JsMacrosSettings() {}

    public static Map<String, Object> snapshot(Object manager) {
        Map<String, Object> groups = new LinkedHashMap<String, Object>();
        if (manager == null) return groups;
        synchronized (manager) {
            try {
                Map<?, ?> classes = mapField(manager, "optionClasses");
                Map<?, ?> options = mapField(manager, "options");
                for (Map.Entry<?, ?> entry : classes.entrySet()) {
                    if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof Class<?>)) continue;
                    Class<?> type = (Class<?>) entry.getValue();
                    Object instance = options.get(type);
                    if (instance == null) continue;
                    Map<String, Object> values = new LinkedHashMap<String, Object>();
                    for (Field field : type.getFields()) {
                        if (!editable(field)) continue;
                        try {
                            values.put(field.getName(), copy(field.get(instance)));
                        } catch (Exception e) {
                            SynapMcShared.error("[SynapMc] Could not read JsMacros setting " + field.getName() + ": " + e.getMessage());
                        }
                    }
                    groups.put((String) entry.getKey(), values);
                }
            } catch (Exception e) {
                SynapMcShared.error("[SynapMc] Could not read JsMacros config: " + e.getMessage());
            }
        }
        return groups;
    }

    public static boolean save(Object manager, String name, Object value) {
        if (manager == null || name == null || name.isEmpty()) return false;
        synchronized (manager) {
            try {
                Map<?, ?> classes = mapField(manager, "optionClasses");
                Map<?, ?> options = mapField(manager, "options");
                String group = null;
                String fieldName = name;
                int separator = name.indexOf('.');
                if (separator >= 0) {
                    group = name.substring(0, separator);
                    fieldName = name.substring(separator + 1);
                }
                Field match = null;
                Object owner = null;
                for (Map.Entry<?, ?> entry : classes.entrySet()) {
                    if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof Class<?>)) continue;
                    if (group != null && !group.equals(entry.getKey())) continue;
                    Class<?> type = (Class<?>) entry.getValue();
                    Object instance = options.get(type);
                    if (instance == null) continue;
                    try {
                        Field candidate = type.getField(fieldName);
                        if (!editable(candidate)) continue;
                        if (match != null) return false;
                        match = candidate;
                        owner = instance;
                    } catch (NoSuchFieldException ignored) { }
                }
                if (match == null) return false;

                Object converted = convert(value, match.getType(), match.getGenericType());
                Object previous = match.get(owner);
                Field rawOptionsField = manager.getClass().getField("rawOptions");
                JsonElement rawOptions = (JsonElement) rawOptionsField.get(manager);
                JsonElement previousOptions = rawOptions == null ? null : rawOptions.deepCopy();
                File configFile = (File) manager.getClass().getField("configFile").get(manager);
                match.set(owner, converted);
                try {
                    Method save = manager.getClass().getMethod("saveConfig");
                    save.invoke(manager);
                    if (configFile == null || !configFile.isFile()) throw new IOException("JsMacros config file is missing");
                    String saved = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
                    JsonElement savedOptions = new JsonParser().parse(saved);
                    if (!savedOptions.equals(rawOptionsField.get(manager))) {
                        throw new IOException("JsMacros config file does not match the requested settings");
                    }
                    return true;
                } catch (Exception e) {
                    match.set(owner, previous);
                    rawOptionsField.set(manager, previousOptions);
                    throw e;
                }
            } catch (Exception e) {
                SynapMcShared.error("[SynapMc] Could not save JsMacros setting " + name + ": " + e.getMessage());
                return false;
            }
        }
    }

    private static boolean editable(Field field) {
        int modifiers = field.getModifiers();
        return Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers)
                && !Modifier.isFinal(modifiers) && !Modifier.isTransient(modifiers)
                && !field.isSynthetic();
    }

    private static Map<?, ?> mapField(Object manager, String name) throws Exception {
        Object value = manager.getClass().getField(name).get(manager);
        if (!(value instanceof Map<?, ?>)) throw new IllegalStateException(name + " is unavailable");
        return (Map<?, ?>) value;
    }

    private static Object copy(Object value) {
        return value == null ? null : GSON.fromJson(GSON.toJsonTree(value), Object.class);
    }

    private static Object convert(Object value, Class<?> type, Type genericType) {
        if (value == null) {
            if (type.isPrimitive()) throw new IllegalArgumentException("A primitive setting cannot be null");
            return null;
        }
        if (type == boolean.class || type == Boolean.class) {
            if (!(value instanceof Boolean)) throw new IllegalArgumentException("Expected a Boolean");
            return value;
        }
        if (type == String.class) {
            if (!(value instanceof String)) throw new IllegalArgumentException("Expected a String");
            return value;
        }
        if (type == int.class || type == Integer.class || type == long.class || type == Long.class
                || type == short.class || type == Short.class || type == byte.class || type == Byte.class) {
            if (!(value instanceof Number)) throw new IllegalArgumentException("Expected an integer");
            long integer;
            try {
                integer = new BigDecimal(value.toString()).toBigIntegerExact().longValueExact();
            } catch (NumberFormatException | ArithmeticException e) {
                throw new IllegalArgumentException("Expected an integer in range", e);
            }
            if (type == int.class || type == Integer.class) return Math.toIntExact(integer);
            if (type == short.class || type == Short.class) {
                if (integer < Short.MIN_VALUE || integer > Short.MAX_VALUE) throw new IllegalArgumentException("Out of range");
                return (short) integer;
            }
            if (type == byte.class || type == Byte.class) {
                if (integer < Byte.MIN_VALUE || integer > Byte.MAX_VALUE) throw new IllegalArgumentException("Out of range");
                return (byte) integer;
            }
            return integer;
        }
        if (type == float.class || type == Float.class || type == double.class || type == Double.class) {
            if (!(value instanceof Number) || !Double.isFinite(((Number) value).doubleValue())) {
                throw new IllegalArgumentException("Expected a finite number");
            }
            if (type == float.class || type == Float.class) {
                float number = ((Number) value).floatValue();
                if (!Float.isFinite(number)) throw new IllegalArgumentException("Float out of range");
                return number;
            }
            return ((Number) value).doubleValue();
        }
        if (type.isEnum()) {
            if (!(value instanceof String)) throw new IllegalArgumentException("Expected an enum name");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object member = Enum.valueOf((Class<? extends Enum>) type, (String) value);
            return member;
        }
        if (Map.class.isAssignableFrom(type)) {
            if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException("Expected a map");
            return GSON.fromJson(GSON.toJsonTree(value), genericType);
        }
        if (Collection.class.isAssignableFrom(type)) {
            if (!(value instanceof Collection<?>)) throw new IllegalArgumentException("Expected a list");
            return GSON.fromJson(GSON.toJsonTree(value), genericType);
        }
        if (type.isInstance(value)) return value;
        throw new IllegalArgumentException("Unsupported setting type: " + type.getSimpleName());
    }
}
