package com.synapmc;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Isolated resource models: the original models/PNGs remain available to custom-model items. */
public final class ItemTextureModels {
    public record Replacement(String model, String originalModel, Map<String, byte[]> assets) {
        public Replacement { assets = Map.copyOf(assets); }
    }
    public record Snapshot(long revision, Map<Object, Replacement> items, Map<String, byte[]> assets) {}
    private static final Map<Object, Replacement> requested = new IdentityHashMap<>();
    private static volatile Map<Object, Replacement> active = Map.of();
    private static volatile Snapshot loading;
    private static long revision;
    private ItemTextureModels() {}

    static synchronized void set(Object item, Replacement replacement) { requested.put(item, replacement); revision++; }
    static synchronized boolean remove(Object item) { if (requested.remove(item) == null) return false; revision++; return true; }
    public static synchronized Snapshot snapshot() {
        Map<Object, Replacement> items = Collections.unmodifiableMap(new IdentityHashMap<>(requested));
        Map<String, byte[]> assets = new HashMap<>();
        for (var replacement : items.values()) assets.putAll(replacement.assets());
        if (!assets.isEmpty()) {
            byte[] atlas = "{\"sources\":[{\"type\":\"minecraft:directory\",\"source\":\"item_texture\",\"prefix\":\"item_texture/\"}]}".getBytes(StandardCharsets.UTF_8);
            assets.put("minecraft:atlases/blocks.json", atlas);
            assets.put("minecraft:atlases/items.json", atlas);
        }
        return new Snapshot(revision, items, Map.copyOf(assets));
    }
    public static void beginReload(Snapshot snapshot) { loading = snapshot; }
    public static Snapshot loading() { Snapshot current = loading; return current == null ? snapshot() : current; }
    public static void activate(Snapshot snapshot) { active = snapshot.items(); }
    public static boolean hasActive() { return !active.isEmpty(); }
    public static boolean hasItem(Object item) { return active.containsKey(item); }
    public static String model(Object item, boolean custom, String incomingModel) {
        if (custom) return null;
        Replacement replacement = active.get(item);
        return replacement != null && (incomingModel == null || replacement.originalModel().equals(incomingModel)) ? replacement.model() : null;
    }

    static Replacement build(ItemTextureBackend backend, String item, String originalModel, String mainPng,
            byte[] png, byte[] metadata) throws IOException {
        Builder builder = new Builder(backend, item, mainPng);
        String root = "synapmc:item_texture/" + item.replace(':', '/') + "/root";
        String texture = root + "/texture";
        builder.texture = texture;
        builder.assets.put(resource(texture, "textures", ".png"), png);
        if (metadata != null) builder.assets.put(resource(texture, "textures", ".png.mcmeta"), metadata);
        if (backend.legacyModels()) {
            String cloned = builder.cloneModel(modelPath(originalModel));
            builder.assets.put(resource(root, "models/item", ".json"), builder.assets.get(resource(cloned, "models", ".json")));
        } else {
            JsonObject definition = builder.json(resource(originalModel, "items", ".json"));
            builder.remapDefinition(definition);
            builder.assets.put(resource(root, "items", ".json"), encode(definition));
        }
        if (!builder.replaced) throw new IOException("The main PNG could not be located in this item's model definitions: " + mainPng);
        return new Replacement(root, originalModel, builder.assets);
    }
    private static String modelPath(String item) {
        String[] parts = qualified(item).split(":", 2);
        return parts[0] + ":item/" + parts[1];
    }
    static String qualified(String id) { return id.contains(":") ? id : "minecraft:" + id; }
    static String resource(String id, String directory, String suffix) {
        String[] parts = qualified(id).split(":", 2);
        return parts[0] + ":" + directory + "/" + parts[1] + suffix;
    }
    private static byte[] encode(JsonElement json) { return json.toString().getBytes(StandardCharsets.UTF_8); }
    private static final class Builder {
        final ItemTextureBackend backend;
        final String prefix, main;
        final Map<String, byte[]> assets = new HashMap<>();
        final Map<String, String> models = new HashMap<>();
        final Set<String> visiting = new HashSet<>();
        String texture;
        boolean replaced;
        Builder(ItemTextureBackend backend, String item, String main) {
            this.backend = backend; this.main = main;
            prefix = "synapmc:item_texture/" + item.replace(':', '/') + "/models/";
        }
        JsonObject json(String id) throws IOException {
            byte[] bytes = backend.readAsset(id);
            if (bytes == null) throw new IOException("No native model resource: " + id);
            try { return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject(); }
            catch (RuntimeException error) { throw new IOException("Invalid native model resource: " + id, error); }
        }
        String cloneModel(String model) throws IOException {
            model = qualified(model);
            if (model.startsWith("minecraft:builtin/")) return model;
            if (visiting.contains(model)) throw new IOException("Cyclic item model parent: " + model);
            if (models.containsKey(model)) return models.get(model);
            visiting.add(model);
            JsonObject json = json(resource(model, "models", ".json"));
            String cloned = prefix + model.replace(':', '/');
            if (json.has("parent")) json.addProperty("parent", cloneModel(json.get("parent").getAsString()));
            if (json.has("textures")) for (var entry : json.getAsJsonObject("textures").entrySet()) {
                String value = entry.getValue().getAsString();
                if (!value.startsWith("#") && resource(value, "textures", ".png").equals(main)) {
                    entry.setValue(new JsonPrimitive(texture)); replaced = true;
                }
            }
            // Preserve original custom-model branches; clone ordinary damage/use variants.
            if (json.has("overrides")) for (var value : json.getAsJsonArray("overrides")) {
                JsonObject override = value.getAsJsonObject();
                JsonObject predicate = override.has("predicate") ? override.getAsJsonObject("predicate") : new JsonObject();
                if (!predicate.has("custom_model_data") && !predicate.has("minecraft:custom_model_data"))
                    override.addProperty("model", cloneModel(override.get("model").getAsString()));
            }
            visiting.remove(model); models.put(model, cloned);
            assets.put(resource(cloned, "models", ".json"), encode(json));
            return cloned;
        }
        void remapDefinition(JsonElement value) throws IOException {
            if (value.isJsonArray()) { for (var child : value.getAsJsonArray()) remapDefinition(child); }
            else if (value.isJsonObject()) for (var entry : value.getAsJsonObject().entrySet()) {
                if ((entry.getKey().equals("model") || entry.getKey().equals("base")) && entry.getValue().isJsonPrimitive()
                        && entry.getValue().getAsJsonPrimitive().isString())
                    entry.setValue(new JsonPrimitive(cloneModel(entry.getValue().getAsString())));
                else remapDefinition(entry.getValue());
            }
        }
    }
}
