package com.synapmc;

import static com.synapmc.NativeTextureAccess.*;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Runtime resource pack and normal Minecraft model selection for item-wide textures. */
public final class NativeItemTextures {
    private static final ThreadLocal<Boolean> originals = ThreadLocal.withInitial(() -> false);
    private static boolean reloading;
    private NativeItemTextures() {}
    static <T> T originalModels(Supplier<T> action) {
        boolean previous = originals.get(); originals.set(true);
        try { return action.get(); } finally { originals.set(previous); }
    }
    static Object component(Object stack, String named, String intermediary) {
        Object key = field(type(reloaded() ? "net.minecraft.core.component.DataComponents" : "net.minecraft.class_9334"), named, intermediary);
        return call(stack, new String[] {"get", "method_58694"}, key);
    }
    /** Redirects only the resolver's item-model component read; no stack/component data is modified. */
    public static Object modelComponent(Object stack, Object original) {
        if (original == null || !ItemTextureModels.hasActive() || originals.get()) return original;
        Object item = call(stack, new String[] {"getItem", "method_7909"});
        if (!ItemTextureModels.hasItem(item)) return original;
        String replacement = ItemTextureModels.model(item, component(stack, "CUSTOM_MODEL_DATA", "field_49637") != null, original.toString());
        return replacement == null ? original : identifier(replacement);
    }
    public static Object legacyModel(Object stack) {
        if (!ItemTextureModels.hasActive() || originals.get()) return null;
        Object item = call(stack, new String[] {"method_7909"});
        if (!ItemTextureModels.hasItem(item)) return null;
        String replacement = ItemTextureModels.model(item, component(stack, "CUSTOM_MODEL_DATA", "field_49637") != null, null);
        if (replacement == null) return null;
        Object manager = call(client(), new String[] {"method_1554"});
        Object id = construct("net.minecraft.class_1091", identifier(replacement), "inventory");
        return call(manager, new String[] {"method_4742"}, id);
    }
    /** Register generated Legacy inventory models before the loader resolves textures and bakes. */
    public static void registerLegacyModels(Object loader) {
        try {
            var register = loader.getClass().getDeclaredMethod("method_61073", type("net.minecraft.class_1091"));
            register.setAccessible(true);
            for (var replacement : ItemTextureModels.loading().items().values())
                register.invoke(loader, construct("net.minecraft.class_1091", identifier(replacement.model()), "inventory"));
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Could not register item texture models", error); }
    }
    public static List<?> appendPack(List<?> packs) {
        var snapshot = ItemTextureModels.loading();
        if (snapshot.assets().isEmpty()) return packs;
        var result = new ArrayList<Object>(packs);
        result.add(pack(snapshot.assets()));
        return result;
    }
    private static Object pack(Map<String, byte[]> assets) {
        Class<?> api = type(reloaded() ? "net.minecraft.server.packs.PackResources" : "net.minecraft.class_3262");
        Class<?> io = type(reloaded() ? "net.minecraft.server.packs.resources.IoSupplier" : "net.minecraft.class_7367");
        Object clientResources = field(type(reloaded() ? "net.minecraft.server.packs.PackType" : "net.minecraft.class_3264"), "CLIENT_RESOURCES", "field_14188");
        Object title = call(type(reloaded() ? "net.minecraft.network.chat.Component" : "net.minecraft.class_2561"), new String[] {"literal", "method_43470"}, "Synapmc item textures");
        Object source = field(type(reloaded() ? "net.minecraft.server.packs.repository.PackSource" : "net.minecraft.class_5352"), "BUILT_IN", "field_25348");
        Object location = construct(reloaded() ? "net.minecraft.server.packs.PackLocationInfo" : "net.minecraft.class_9224", "synapmc-item-textures", title, source, Optional.empty());
        Set<String> namespaces = new HashSet<>();
        for (String id : assets.keySet()) namespaces.add(id.substring(0, id.indexOf(':')));
        return Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[] {api}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getNamespaces", "method_14406": return args[0] == clientResources ? Set.copyOf(namespaces) : Set.of();
                case "getResource", "method_14405": {
                    byte[] bytes = args[0] == clientResources ? assets.get(args[1].toString()) : null;
                    return bytes == null ? null : input(io, bytes);
                }
                case "listResources", "method_14408": {
                    if (args[0] != clientResources) return null;
                    String prefix = args[1] + ":" + args[2] + "/";
                    for (var entry : assets.entrySet()) if (entry.getKey().startsWith(prefix))
                        call(args[3], new String[] {"accept"}, identifier(entry.getKey()), input(io, entry.getValue()));
                    return null;
                }
                case "location", "method_56926": return location;
                case "packId", "method_14409": return "synapmc-item-textures";
                case "knownPackInfo", "method_56929": return Optional.empty();
                case "getRootResource", "method_14410", "getMetadataSection", "method_14407", "close": return null;
                case "toString": return "SynapmcItemTexturePack";
                case "hashCode": return System.identityHashCode(proxy);
                case "equals": return proxy == args[0];
                default: throw new UnsupportedOperationException("Unknown resource pack method: " + method);
            }
        });
    }
    private static Object input(Class<?> io, byte[] bytes) {
        return Proxy.newProxyInstance(io.getClassLoader(), new Class<?>[] {io}, (proxy, method, args) -> {
            if (method.getName().equals("get")) return new ByteArrayInputStream(bytes);
            if (method.getName().equals("toString")) return "SynapmcTextureInput";
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            if (method.getName().equals("equals")) return proxy == args[0];
            throw new UnsupportedOperationException(method.toString());
        });
    }
    public static synchronized void requestReload() {
        if (reloading) return;
        reloading = true;
        try { call(client(), new String[] {"execute"}, (Runnable) NativeItemTextures::reload); }
        catch (RuntimeException error) { reloading = false; throw error; }
    }
    private static void reload() {
        var snapshot = ItemTextureModels.snapshot();
        ItemTextureModels.beginReload(snapshot);
        try {
            var future = (CompletableFuture<?>) call(client(), new String[] {"reloadResourcePacks", "method_1521"});
            future.whenComplete((unused, failure) -> finishReload(snapshot, failure));
        } catch (Throwable error) { finishReload(snapshot, error); }
    }
    private static synchronized void finishReload(ItemTextureModels.Snapshot snapshot, Throwable failure) {
        if (failure == null) ItemTextureModels.activate(snapshot);
        else SynapMcShared.error("[SynapMc] Item texture resource reload failed: " + failure);
        reloading = false;
        // Coalesce requests received during a reload, then bake the newest snapshot once.
        if (ItemTextureModels.snapshot().revision() != snapshot.revision()) requestReload();
    }
}
