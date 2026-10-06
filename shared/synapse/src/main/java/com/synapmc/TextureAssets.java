package com.synapmc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class TextureAssets {
    private static final String PACK_FOLDER = "SynapMc Textures";
    private TextureAssets() {}

    public static final class Asset {
        public final String id;
        public final byte[] bytes;
        public final String source;

        private Asset(String id, byte[] bytes, String source) {
            this.id = id;
            this.bytes = bytes;
            this.source = source;
        }

        public Map<String, Object> describe() {
            Map<String, Object> info = new LinkedHashMap<String, Object>();
            String path = id.substring(id.indexOf(':') + 1);
            int slash = path.lastIndexOf('/');
            info.put("id", id);
            info.put("name", slash < 0 ? path : path.substring(slash + 1));
            info.put("dir", "assets/" + id.substring(0, id.indexOf(':')) + "/"
                    + (slash < 0 ? "" : path.substring(0, slash + 1)));
            info.put("source", source);
            return info;
        }
    }

    public static String itemId(Object helper) {
        if (helper instanceof String) return normalizeItem((String) helper);
        if (helper == null) throw new IllegalArgumentException("ItemStackHelper is required");
        Object id = call(helper, new String[] {"getItemId", "getItemID"});
        if (!(id instanceof String)) throw new IllegalArgumentException("Expected a JsMacros ItemStackHelper");
        return normalizeItem((String) id);
    }

    public static String normalizeItem(String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("Item name is empty");
        String id = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (id.indexOf(':') < 0) id = "minecraft:" + id;
        return normalizeId(id);
    }

    public static String normalizeId(String value) {
        if (value == null || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                || value.contains("..") || value.endsWith("/")) {
            throw new IllegalArgumentException("Invalid resource ID: " + value);
        }
        return value;
    }

    public static Map<String, Object> describeItem(Object helper) {
        String item = itemId(helper);
        String namespace = namespace(item);
        String name = path(item);
        Set<String> textures = new LinkedHashSet<String>();
        Set<String> models = new LinkedHashSet<String>();
        Set<String> clientItems = new LinkedHashSet<String>();
        String modelId = selectedItemModel(helper);
        String clientItem = modelId == null ? namespace + ":items/" + name + ".json"
                : namespace(modelId) + ":items/" + path(modelId) + ".json";
        Asset definition = read(clientItem);
        if (definition == null && modelId != null) {
            clientItem = namespace + ":items/" + name + ".json";
            definition = read(clientItem);
        }
        if (definition != null) {
            clientItems.add(clientItem);
            inspectJson(definition.bytes, namespace, textures, models, new LinkedHashSet<String>(), 0);
        }
        if (models.isEmpty()) inspectModel(namespace + ":models/item/" + name + ".json",
                textures, models, new LinkedHashSet<String>(), 0);
        String fallback = namespace + ":textures/item/" + name + ".png";
        if (textures.isEmpty() && read(fallback) != null) textures.add(fallback);

        List<Map<String, Object>> png = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> mcmeta = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> model = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> itemFiles = new ArrayList<Map<String, Object>>();
        for (String id : textures) addIfPresent(png, id);
        for (String id : textures) addIfPresent(mcmeta, id + ".mcmeta");
        for (String id : models) addIfPresent(model, id);
        for (String id : clientItems) addIfPresent(itemFiles, id);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("item", item);
        result.put("png", png);
        result.put("mcmeta", mcmeta);
        result.put("model", model);
        result.put("clientItem", itemFiles);
        result.put("note", "Model resources include possible branches; custom renderers may use other textures.");
        return result;
    }

    private static String selectedItemModel(Object helper) {
        if (helper == null || helper instanceof String) return null;
        Object stack = call(helper, new String[] {"getRaw"});
        if (stack == null) return null;
        for (String className : new String[] {
                "net.minecraft.core.component.DataComponents",
                "net.minecraft.component.DataComponentTypes",
                "net.minecraft.class_9334"
        }) {
            try {
                Class<?> types = Class.forName(className);
                for (String fieldName : new String[] {"ITEM_MODEL", "field_54199"}) {
                    try {
                        Object component = types.getField(fieldName).get(null);
                        Object model = call(stack, new String[] {"get", "method_57381"}, component);
                        if (model != null) return normalizeItem(model.toString());
                    } catch (NoSuchFieldException ignored) { }
                }
            } catch (Exception ignored) { }
        }
        return null;
    }

    private static void addIfPresent(List<Map<String, Object>> list, String id) {
        Asset asset = read(id);
        if (asset != null) list.add(asset.describe());
    }

    private static void inspectModel(String id, Set<String> textures, Set<String> models,
                                     Set<String> seen, int depth) {
        if (depth > 20 || !seen.add(id)) return;
        Asset asset = read(id);
        if (asset == null) return;
        models.add(id);
        inspectJson(asset.bytes, namespace(id), textures, models, seen, depth + 1);
    }

    private static void inspectJson(byte[] bytes, String namespace, Set<String> textures,
                                    Set<String> models, Set<String> seen, int depth) {
        try {
            JsonElement root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            inspectElement(root, namespace, textures, models, seen, depth);
        } catch (RuntimeException error) {
            SynapMcShared.error("[SynapMc] Could not read item model: " + error.getMessage());
        }
    }

    private static void inspectElement(JsonElement element, String namespace, Set<String> textures,
                                       Set<String> models, Set<String> seen, int depth) {
        if (depth > 20 || element == null) return;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray())
                inspectElement(child, namespace, textures, models, seen, depth + 1);
            return;
        }
        if (!element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                String raw = value.getAsString();
                if (key.equals("parent") || key.equals("model")) {
                    String reference = assetReference(raw, namespace);
                    if (reference != null && !reference.startsWith("minecraft:builtin/"))
                        inspectModel(namespace(reference) + ":models/" + path(reference) + ".json",
                                textures, models, seen, depth + 1);
                } else if (key.equals("texture")) {
                    addTexture(raw, namespace, textures);
                }
            } else if (key.equals("textures") && value.isJsonObject()) {
                for (Map.Entry<String, JsonElement> texture : value.getAsJsonObject().entrySet()) {
                    if (texture.getValue().isJsonPrimitive())
                        addTexture(texture.getValue().getAsString(), namespace, textures);
                }
            } else {
                inspectElement(value, namespace, textures, models, seen, depth + 1);
            }
        }
    }

    private static void addTexture(String raw, String namespace, Set<String> textures) {
        if (raw.startsWith("#")) return;
        String reference = assetReference(raw, namespace);
        if (reference == null) return;
        String name = path(reference);
        if (!name.startsWith("textures/")) name = "textures/" + name;
        if (!name.endsWith(".png")) name += ".png";
        textures.add(namespace(reference) + ":" + name);
    }

    private static String assetReference(String value, String namespace) {
        if (value == null || value.isEmpty() || value.startsWith("#")) return null;
        try { return normalizeId(value.indexOf(':') < 0 ? namespace + ":" + value : value); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    public static Asset read(String id) {
        try {
            Object client = client();
            if (client == null) return null;
            Object manager = call(client, new String[] {"getResourceManager", "method_1478"});
            Object identifier = identifier(normalizeId(id));
            if (manager == null || identifier == null) return null;
            Object optional = call(manager, new String[] {"getResource", "method_14486"}, identifier);
            if (!(optional instanceof Optional) || !((Optional<?>) optional).isPresent()) return null;
            Object resource = ((Optional<?>) optional).get();
            Object stream = call(resource, new String[] {"getInputStream", "open", "method_14482"});
            if (!(stream instanceof InputStream)) return null;
            try (InputStream input = (InputStream) stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (output.size() + count > 32 * 1024 * 1024) throw new IOException("Resource exceeds 32 MiB");
                    output.write(buffer, 0, count);
                }
                Object source = call(resource, new String[] {"getPackId", "sourcePackId", "method_14480"});
                return new Asset(id, output.toByteArray(), source == null ? "unknown" : source.toString());
            }
        } catch (Exception error) {
            SynapMcShared.error("[SynapMc] Could not read resource " + id + ": " + error.getMessage());
            return null;
        }
    }

    public static Path exportRoot() { return new File(SynapMcShared.roamingDir, "assets/textures").toPath(); }

    public static Path packRoot() {
        return new File(MinecraftAccess.gameDirectory(), "resourcepacks/" + PACK_FOLDER).toPath();
    }

    public static Path packFile(String id) {
        String safe = normalizeId(id);
        Path root = packRoot().toAbsolutePath().normalize();
        Path file = root.resolve("assets").resolve(namespace(safe)).resolve(path(safe)).normalize();
        if (!file.startsWith(root)) throw new IllegalArgumentException("Resource path escapes pack");
        return file;
    }

    public static void ensurePack() throws IOException {
        Path root = packRoot();
        Files.createDirectories(root);
        int format = SynapMcShared.core.getJsVersion().equals("legacy") ? 9
                : SynapMcShared.core.getJsVersion().equals("community") ? 64 : 88;
        String metadata = SynapMcShared.core.getJsVersion().equals("reload")
                ? "{\"pack\":{\"min_format\":[" + format + ",0],\"max_format\":[" + format
                    + ",0],\"description\":\"SynapMc texture overrides\"}}"
                : "{\"pack\":{\"pack_format\":" + format
                    + ",\"description\":\"SynapMc texture overrides\"}}";
        Files.write(root.resolve("pack.mcmeta"), metadata.getBytes(StandardCharsets.UTF_8));
    }

    public static void activateAndReload() {
        MacroScreenBridge.onClient(() -> {
            try {
                Object client = client();
                Object manager = call(client, new String[] {"getResourcePackManager", "getResourcePackRepository", "method_1520"});
                if (manager == null) throw new IOException("Resource pack manager unavailable");
                required(manager, new String[] {"scanPacks", "reload", "method_14445"});
                Object available = call(manager, new String[] {"getIds", "getAvailableIds", "method_29206"});
                String selected = null;
                if (available instanceof Collection) {
                    for (Object id : (Collection<?>) available) {
                        String candidate = String.valueOf(id);
                        if (candidate.endsWith("/" + PACK_FOLDER) || candidate.equals(PACK_FOLDER)) {
                            selected = candidate;
                            break;
                        }
                    }
                }
                if (selected == null) throw new IOException("SynapMc resource pack was not discovered");
                Object enabled = call(manager, new String[] {"getEnabledIds", "getSelectedIds", "method_29210"});
                List<String> ids = new ArrayList<String>();
                if (enabled instanceof Collection)
                    for (Object id : (Collection<?>) enabled) if (!selected.equals(id)) ids.add(String.valueOf(id));
                ids.add(selected);
                required(manager, new String[] {"setEnabledProfiles", "setSelected", "method_14447"}, ids);
                Object options = field(client, "options", "field_1690");
                Object list = field(options, "resourcePacks", "field_1887");
                if (list instanceof List) {
                    @SuppressWarnings("unchecked") List<String> configured = (List<String>) list;
                    configured.remove(selected);
                    configured.add(selected);
                }
                call(options, new String[] {"write", "method_1640", "save"});
                required(client, new String[] {"reloadResources", "reloadResourcePacks", "method_1513"});
            } catch (Exception error) {
                SynapMcShared.error("[SynapMc] Could not activate texture pack: " + error);
            }
        });
    }

    private static String namespace(String id) { return id.substring(0, id.indexOf(':')); }
    private static String path(String id) { return id.substring(id.indexOf(':') + 1); }

    private static Object client() {
        for (String[] candidate : new String[][] {
                {"net.minecraft.client.Minecraft", "getInstance"},
                {"net.minecraft.client.MinecraftClient", "getInstance"},
                {"net.minecraft.class_310", "method_1551"}
        }) {
            try { return Class.forName(candidate[0]).getMethod(candidate[1]).invoke(null); }
            catch (Exception ignored) { }
        }
        return null;
    }

    private static Object identifier(String id) throws Exception {
        for (String[] candidate : new String[][] {
                {"net.minecraft.resources.ResourceLocation", "parse"},
                {"net.minecraft.util.Identifier", "of"},
                {"net.minecraft.class_2960", "method_12829"}
        }) {
            try { return Class.forName(candidate[0]).getMethod(candidate[1], String.class).invoke(null, id); }
            catch (ClassNotFoundException | NoSuchMethodException ignored) { }
        }
        throw new ClassNotFoundException("Minecraft resource identifier");
    }

    private static Object call(Object target, String[] names, Object... arguments) {
        if (target == null) return null;
        for (String name : names) for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) continue;
            boolean matches = true;
            for (int index = 0; index < arguments.length; index++) {
                if (arguments[index] != null && !method.getParameterTypes()[index].isInstance(arguments[index])) {
                    matches = false;
                    break;
                }
            }
            if (!matches) continue;
            try { return method.invoke(target, arguments); }
            catch (Exception ignored) { }
        }
        return null;
    }

    private static Object required(Object target, String[] names, Object... arguments) throws Exception {
        if (target == null) throw new NoSuchMethodException(names[0]);
        for (String name : names) for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) continue;
            boolean matches = true;
            for (int index = 0; index < arguments.length; index++) {
                if (arguments[index] != null && !method.getParameterTypes()[index].isInstance(arguments[index])) {
                    matches = false;
                    break;
                }
            }
            if (matches) return method.invoke(target, arguments);
        }
        throw new NoSuchMethodException(names[0]);
    }

    private static Object field(Object owner, String... names) {
        if (owner == null) return null;
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            for (String name : names) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(owner);
                } catch (Exception ignored) { }
            }
        }
        return null;
    }
}
