package com.synapmc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Native model/resource access across the supported Minecraft editions. No JSON variant guessing. */
public final class NativeTextureAccess implements ItemTextureBackend {
    public static final NativeTextureAccess INSTANCE = new NativeTextureAccess();
    private record RegisteredImage(Object custom, Object element) {}
    private NativeTextureAccess() {}

    @Override public String itemId(Object item) {
        Object registry = field(type(reloaded() ? "net.minecraft.core.registries.BuiltInRegistries" : "net.minecraft.class_7923"), "ITEM", "field_41178");
        return call(registry, new String[] {"getKey", "method_10221"}, item).toString();
    }
    @Override public String defaultModelId(Object item) {
        if (legacyModels()) return itemId(item);
        Object model = NativeItemTextures.component(call(item, new String[] {"getDefaultInstance", "method_7854"}), "ITEM_MODEL", "field_54199");
        if (model == null) throw new IllegalStateException("Item has no default item model");
        return model.toString();
    }
    @Override public List<String> defaultIdentifiers(Object item) {
        return onClient(() -> NativeItemTextures.originalModels(() -> identifiers(call(item, new String[] {"getDefaultInstance", "method_7854"}))));
    }
    @Override public boolean legacyModels() { return "legacy".equals(edition()); }
    @Override public void reloadItemTextures() { NativeItemTextures.requestReload(); }

    @Override public List<String> identifiers(Object stack) {
        return onClient(() -> {
            Object client = client();
            Object world = field(client, "level", "field_1687");
            Object player = field(client, "player", "field_1724");
            LinkedHashSet<String> ids = new LinkedHashSet<>();
            boolean reloaded = reloaded();
            if (reloaded || "community".equals(edition())) {
                Object state = construct(reloaded ? "net.minecraft.client.renderer.item.ItemStackRenderState" : "net.minecraft.class_10444");
                Object resolver = call(client, new String[] {"getItemModelResolver", "method_65386"});
                Object gui = field(type(reloaded ? "net.minecraft.world.item.ItemDisplayContext" : "net.minecraft.class_811"), "GUI", "field_4317");
                call(resolver, new String[] {"updateForTopItem", "method_65598"}, state, stack, gui, world, player, 0);
                Object layers = field(state, "layers", "field_55340");
                int count = ((Number) field(state, "activeLayerCount", "field_55339")).intValue();
                for (int i = 0; i < count; i++) for (Object quad : (Iterable<?>) field(Array.get(layers, i), "quads", "field_56964")) {
                    Object sprite = reloaded ? call(call(quad, new String[] {"materialInfo"}), new String[] {"sprite"})
                        : call(quad, new String[] {"comp_3724"});
                    addSprite(ids, client, sprite);
                }
            } else {
                Object renderer = call(client, new String[] {"method_1480"});
                Object model = call(renderer, new String[] {"method_4019"}, stack, world, player, 0);
                Object random = call(type("net.minecraft.class_5819"), new String[] {"method_43049"}, 42L);
                List<Object> faces = new ArrayList<>();
                faces.add(null);
                faces.addAll(List.of(type("net.minecraft.class_2350").getEnumConstants()));
                for (Object face : faces) {
                    call(random, new String[] {"method_43052"}, 42L);
                    for (Object quad : (Iterable<?>) call(model, new String[] {"method_4707"}, null, face, random))
                        addSprite(ids, client, call(quad, new String[] {"method_35788"}));
                }
            }
            return List.copyOf(ids);
        });
    }

    private static void addSprite(LinkedHashSet<String> ids, Object client, Object sprite) {
        Object contents = call(sprite, new String[] {"contents", "method_45851"});
        String spriteId = call(contents, new String[] {"name", "method_45816"}).toString();
        String png = png(spriteId);
        if (resource(client, png) == null) {
            String atlas = call(sprite, new String[] {"atlasLocation", "method_45852"}).toString();
            png = atlasSource(client, atlas, spriteId);
        }
        if (png != null && resource(client, png) != null) ids.add(png);
    }

    /** Atlas single/directory sources can give sprites names different from their PNG resource. */
    private static String atlasSource(Object client, String atlas, String spriteId) {
        String[] atlasParts = qualified(atlas).split(":", 2);
        String atlasPath = atlasParts[1];
        if (!atlasPath.startsWith("textures/atlas/") || !atlasPath.endsWith(".png")) return null;
        String configId = atlasParts[0] + ":atlases/" + atlasPath.substring(15, atlasPath.length() - 4) + ".json";
        Object manager = call(client, new String[] {"getResourceManager", "method_1478"});
        Object stack = call(manager, new String[] {"getResourceStack", "method_14489"}, identifier(configId));
        String selected = null;
        for (Object resource : (Iterable<?>) stack) {
            try (InputStream input = (InputStream) call(resource, new String[] {"open", "method_14482"});
                    var reader = new InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8)) {
                JsonObject config = JsonParser.parseReader(reader).getAsJsonObject();
                if (!config.has("sources")) continue;
                for (var entry : config.getAsJsonArray("sources")) {
                    JsonObject source = entry.getAsJsonObject();
                    String kind = qualified(source.get("type").getAsString());
                    if (kind.equals("minecraft:single")) {
                        String image = qualified(source.get("resource").getAsString());
                        String alias = source.has("sprite") ? qualified(source.get("sprite").getAsString()) : image;
                        if (alias.equals(qualified(spriteId))) selected = png(image);
                    } else if (kind.equals("minecraft:directory")) {
                        String[] sprite = qualified(spriteId).split(":", 2);
                        String prefix = source.get("prefix").getAsString();
                        if (sprite[1].startsWith(prefix))
                            selected = png(sprite[0] + ":" + source.get("source").getAsString() + "/" + sprite[1].substring(prefix.length()));
                    }
                }
            } catch (IOException | RuntimeException error) {
                throw new IllegalStateException("Could not resolve atlas source " + configId, error);
            }
        }
        return selected;
    }

    @Override public byte[] readAsset(String id) throws IOException {
        try {
            return onClient(() -> {
                Object resource = resource(client(), id);
                if (resource == null) return null;
                try (InputStream input = (InputStream) call(resource, new String[] {"open", "method_14482"})) {
                    return input.readAllBytes();
                } catch (IOException error) { throw new IllegalStateException(error); }
            });
        } catch (RuntimeException error) { throw new IOException("Could not read texture resource " + id, error); }
    }

    private static Object resource(Object client, String id) {
        Object manager = call(client, new String[] {"getResourceManager", "method_1478"});
        return ((Optional<?>) call(manager, new String[] {"getResource", "method_14486"}, identifier(id))).orElse(null);
    }
    static Object identifier(String id) {
        return call(type(reloaded() ? "net.minecraft.resources.Identifier" : "net.minecraft.class_2960"),
            new String[] {"parse", "method_60654"}, id);
    }
    private static String qualified(String id) { return id.contains(":") ? id : "minecraft:" + id; }
    private static String png(String id) {
        String[] parts = qualified(id).split(":", 2);
        String path = parts[1];
        if (!path.startsWith("textures/")) path = "textures/" + path;
        if (!path.endsWith(".png")) path += ".png";
        return parts[0] + ":" + path;
    }

    @Override public Object copyStack(Object stack) { return call(stack, new String[] {"copy", "method_7972"}); }

    @Override public Object registerImage(BufferedImage image, String name) {
        String core = corePackage();
        Object custom = construct(core + ".client.api.classes.CustomImage", image, name);
        String id = call(custom, new String[] {"getIdentifier"}).toString();
        Object element = construct(core + ".client.api.classes.render.components.Image", 0, 0, 16, 16, 0, -1,
            id, 0, 0, image.getWidth(), image.getHeight(), image.getWidth(), image.getHeight(), 0f);
        return new RegisteredImage(custom, element);
    }
    @Override public void updateImage(Object image) { call(((RegisteredImage) image).custom(), new String[] {"update"}); }
    @Override public void drawImage(Object image, Object context, int x, int y) {
        Object element = ((RegisteredImage) image).element();
        call(element, new String[] {"setPos"}, x, y, 16, 16);
        call(element, new String[] {"extractRenderState", "method_25394"}, context, 0, 0, 0f);
    }
    @Override public void releaseImage(Object image) {
        Object custom = ((RegisteredImage) image).custom();
        Object id = field(custom, "identifier");
        ((Map<?, ?>) field(type(corePackage() + ".client.api.classes.CustomImage"), "IMAGES")).remove(id.toString());
        Object manager = call(client(), new String[] {"getTextureManager", "method_1531"});
        call(manager, new String[] {"release", "method_4615"}, id);
    }
    public static void drawVanilla(Object context, Object stack, int x, int y) {
        call(context, new String[] {"item", "method_51427"}, stack, x, y);
    }

    static String edition() {
        String version = SynapMcShared.core == null ? "legacy" : SynapMcShared.core.getJsVersion();
        return "reload".equals(version) ? "reloaded" : version;
    }
    static boolean reloaded() { return "reloaded".equals(edition()); }
    private static String corePackage() { return "community".equals(edition()) ? "com.jsmacrosce.jsmacros" : "xyz.wagyourtail.jsmacros"; }
    static Object client() {
        return call(type(reloaded() ? "net.minecraft.client.Minecraft" : "net.minecraft.class_310"), new String[] {"getInstance", "method_1551"});
    }

    static <T> T onClient(Supplier<T> action) {
        Object client = client();
        boolean main = Boolean.TRUE.equals(call(client, new String[] {"isSameThread", "method_18854"}));
        Object profile = SynapMcShared.core == null ? null : SynapMcShared.core.getProfile();
        boolean joined = profile != null && Boolean.TRUE.equals(call(profile, new String[] {"checkJoinedThreadStack"}));
        // During a joined event the main thread is paused, so scheduling back to it would deadlock.
        if (main || joined) return action.get();
        FutureTask<T> task = new FutureTask<>(action::get);
        call(client, new String[] {"execute"}, task);
        try { return task.get(5, TimeUnit.SECONDS); }
        catch (InterruptedException error) { task.cancel(false); Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
        catch (Exception error) { task.cancel(false); throw new IllegalStateException("Minecraft texture operation did not complete", error); }
    }

    static Class<?> type(String name) {
        try { return Class.forName(name); }
        catch (ClassNotFoundException error) { throw new IllegalStateException("Minecraft texture API is unavailable: " + name, error); }
    }
    static Object construct(String name, Object... args) {
        for (var constructor : type(name).getConstructors()) if (matches(constructor.getParameterTypes(), args)) {
            try { return constructor.newInstance(args); }
            catch (ReflectiveOperationException error) { throw failure(error); }
        }
        throw new IllegalStateException("Missing texture constructor: " + name);
    }
    static Object field(Object target, String... names) {
        Class<?> owner = target instanceof Class<?> type ? type : target.getClass();
        for (String name : names) for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target instanceof Class<?> ? null : target);
            } catch (NoSuchFieldException ignored) { }
            catch (ReflectiveOperationException error) { throw failure(error); }
        }
        throw new IllegalStateException("Missing texture field: " + owner.getName() + " " + List.of(names));
    }
    static Object call(Object target, String[] names, Object... args) {
        Class<?> owner = target instanceof Class<?> type ? type : target.getClass();
        for (String name : names) for (Method method : owner.getMethods())
            if (method.getName().equals(name) && matches(method.getParameterTypes(), args)) {
                try { method.setAccessible(true); return method.invoke(target instanceof Class<?> ? null : target, args); }
                catch (ReflectiveOperationException error) { throw failure(error); }
            }
        throw new IllegalStateException("Missing texture method: " + owner.getName() + " " + List.of(names));
    }
    private static boolean matches(Class<?>[] parameters, Object[] args) {
        if (parameters.length != args.length) return false;
        for (int i = 0; i < args.length; i++) {
            Class<?> parameter = parameters[i];
            if (parameter == int.class) parameter = Integer.class;
            else if (parameter == long.class) parameter = Long.class;
            else if (parameter == float.class) parameter = Float.class;
            else if (parameter == boolean.class) parameter = Boolean.class;
            if (args[i] == null ? parameters[i].isPrimitive() : !parameter.isInstance(args[i])) return false;
        }
        return true;
    }
    private static IllegalStateException failure(ReflectiveOperationException error) {
        return new IllegalStateException("Minecraft texture operation failed", error instanceof InvocationTargetException invocation ? invocation.getCause() : error);
    }
}
