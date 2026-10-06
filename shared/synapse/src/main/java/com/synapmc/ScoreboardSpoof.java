package com.synapmc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class ScoreboardSpoof {
    private static final Map<Key, Layers> layers = new HashMap<Key, Layers>();

    private ScoreboardSpoof() {}

    public static Handle change(int line, String text) {
        if (line < -1 || text == null) return null;
        return onClient(() -> {
            Object client = client();
            Object world = field(client, "world", "level", "field_1687");
            if (world == null) return null;
            Object board = call(world, new String[] {"getScoreboard", "method_8428"});
            Object objective = sidebarObjective(board);
            if (objective == null) return null;
            String owner = line == -1 ? null : lineOwner(board, objective, line);
            if (line >= 0 && owner == null) return null;
            Object access = owner == null ? objective : scoreAccess(board, objective, owner);
            Object before = currentValue(access, owner);
            Object replacement = literal(text);
            Key key = new Key(objective, owner);
            synchronized (layers) {
                Layers state = layers.get(key);
                boolean serverChanged = state != null && !state.active.isEmpty()
                        && before != state.active.get(state.active.size() - 1).replacement;
                setValue(access, owner, replacement);
                if (state == null) {
                    state = new Layers(before);
                    layers.put(key, state);
                } else if (serverChanged) {
                    state.baseline = before;
                    for (Handle old : state.active) old.active = false;
                    state.active.clear();
                }
                Handle handle = new Handle(key, board, replacement);
                state.active.add(handle);
                return handle;
            }
        });
    }

    public static String readText(int line) {
        if (line < -1) return null;
        return onClient(() -> {
            Object client = client();
            Object world = field(client, "world", "level", "field_1687");
            if (world == null) return null;
            Object board = call(world, new String[] {"getScoreboard", "method_8428"});
            Object objective = sidebarObjective(board);
            if (objective == null) return null;
            String owner = line == -1 ? null : lineOwner(board, objective, line);
            if (line >= 0 && owner == null) return null;
            Object access = owner == null ? objective : scoreAccess(board, objective, owner);
            Object text = currentValue(access, owner);
            if (text == null) return null;
            Object value = optionalCall(text, new String[] {"getString", "asString", "method_10851"});
            return value == null ? text.toString() : value.toString();
        });
    }

    private static Object sidebarObjective(Object board) throws Exception {
        if (board == null) return null;
        for (String name : new String[] {"getDisplayObjective", "getObjectiveForSlot", "method_1189"}) {
            for (Method method : board.getClass().getMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != 1) continue;
                Class<?> type = method.getParameterTypes()[0];
                if (type == int.class) return method.invoke(board, 1);
                if (type.isEnum()) {
                    for (Object slot : type.getEnumConstants()) {
                        if (((Enum<?>) slot).name().equals("SIDEBAR")) return method.invoke(board, slot);
                        Object id = optionalCall(slot, new String[] {"getId", "id", "method_52621"});
                        if (id instanceof Number && ((Number) id).intValue() == 1) return method.invoke(board, slot);
                    }
                }
            }
        }
        throw new NoSuchMethodException("sidebar display slot");
    }

    private static String lineOwner(Object board, Object objective, int line) throws Exception {
        Object collection = entries(board, objective);
        if (!(collection instanceof Collection)) return null;
        List<Entry> entries = new ArrayList<Entry>();
        for (Object item : (Collection<?>) collection) {
            String owner = (String) optionalCall(item, new String[] {"owner", "getOwner", "getPlayerName", "method_1129", "comp_2127"});
            if (owner == null || owner.startsWith("#")) continue;
            Object hidden = optionalCall(item, new String[] {"hidden", "isHidden", "method_55385"});
            if (Boolean.TRUE.equals(hidden)) continue;
            Object value = optionalCall(item, new String[] {"value", "getValue", "getScore", "method_1126", "comp_2128"});
            if (value instanceof Number) entries.add(new Entry(owner, ((Number) value).intValue()));
        }
        entries.sort(Comparator.comparingInt((Entry e) -> e.score).reversed()
                .thenComparing(e -> e.owner, String.CASE_INSENSITIVE_ORDER));
        return line < entries.size() && line < 15 ? entries.get(line).owner : null;
    }

    private static Object entries(Object board, Object objective) throws Exception {
        return call(board, new String[] {"getScoreboardEntries", "listPlayerScores", "getAllPlayerScores", "method_1184"}, objective);
    }

    private static boolean hasEntry(Object board, Object objective, String owner) throws Exception {
        Object collection = entries(board, objective);
        if (!(collection instanceof Collection)) return false;
        for (Object item : (Collection<?>) collection) {
            Object name = optionalCall(item, new String[] {"owner", "getOwner", "getPlayerName", "method_1129", "comp_2127"});
            if (owner.equals(name)) return true;
        }
        return false;
    }

    private static Object scoreAccess(Object board, Object objective, String owner) throws Exception {
        Object holder = null;
        for (String name : new String[] {"net.minecraft.world.scores.ScoreHolder", "net.minecraft.scoreboard.ScoreHolder", "net.minecraft.class_9015"}) {
            try {
                Class<?> type = Class.forName(name);
                for (String factory : new String[] {"forNameOnly", "fromName", "method_55422"}) {
                    try { holder = type.getMethod(factory, String.class).invoke(null, owner); break; }
                    catch (NoSuchMethodException ignored) { }
                }
                if (holder != null) break;
            } catch (ClassNotFoundException ignored) { }
        }
        if (holder == null) throw new UnsupportedOperationException("this Minecraft version has no per-line display text");
        Object access = call(board, new String[] {"getOrCreatePlayerScore", "getOrCreateScore", "method_1180"}, holder, objective);
        if (access == null) throw new UnsupportedOperationException("this Minecraft version has no writable score entry");
        return access;
    }

    private static Object currentValue(Object access, String owner) throws Exception {
        return owner == null
                ? call(access, new String[] {"getDisplayName", "method_1114"})
                : call(access, new String[] {"display", "getDisplay", "getDisplayText", "method_55419"});
    }

    private static void setValue(Object access, String owner, Object text) throws Exception {
        if (owner == null) call(access, new String[] {"setDisplayName", "method_1121"}, text);
        else call(access, new String[] {"display", "setDisplay", "setDisplayText", "method_55411"}, text);
    }

    private static Object optionalCall(Object owner, String[] names) {
        try { return call(owner, names); }
        catch (Exception ignored) { return null; }
    }

    private static Object literal(String value) throws Exception {
        for (String type : new String[] {"net.minecraft.network.chat.Component", "net.minecraft.text.Text", "net.minecraft.class_2561"}) {
            try {
                Class<?> textClass = Class.forName(type);
                for (String method : new String[] {"literal", "method_43470"}) {
                    try { return textClass.getMethod(method, String.class).invoke(null, value); }
                    catch (NoSuchMethodException ignored) { }
                }
            } catch (ClassNotFoundException ignored) { }
        }
        throw new NoSuchMethodException("Minecraft text factory");
    }

    private static Object client() throws Exception {
        for (String type : new String[] {"net.minecraft.client.Minecraft", "net.minecraft.client.MinecraftClient", "net.minecraft.class_310"}) {
            try {
                Class<?> clientClass = Class.forName(type);
                for (String method : new String[] {"getInstance", "method_1551"}) {
                    try { return clientClass.getMethod(method).invoke(null); }
                    catch (NoSuchMethodException ignored) { }
                }
            } catch (ClassNotFoundException ignored) { }
        }
        throw new NoSuchMethodException("Minecraft client");
    }

    private static Object field(Object owner, String... names) throws Exception {
        if (owner == null) return null;
        for (String name : names) {
            Class<?> type = owner.getClass();
            while (type != null) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(owner);
                } catch (NoSuchFieldException ignored) { type = type.getSuperclass(); }
            }
        }
        return null;
    }

    private static Object call(Object owner, String[] names, Object... args) throws Exception {
        if (owner == null) return null;
        for (String name : names) {
            for (Method method : owner.getClass().getMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
                boolean compatible = true;
                Class<?>[] parameters = method.getParameterTypes();
                for (int i = 0; i < args.length; i++) {
                    if (args[i] != null && !parameters[i].isInstance(args[i])
                            && !(parameters[i] == int.class && args[i] instanceof Integer)) compatible = false;
                }
                if (compatible) return method.invoke(owner, args);
            }
        }
        throw new NoSuchMethodException(owner.getClass().getName() + "." + String.join("/", names));
    }

    private static <T> T onClient(Callable<T> task) {
        try {
            Object minecraft = client();
            if (Boolean.TRUE.equals(call(minecraft, new String[] {"isOnThread", "isSameThread", "method_18854"}))) return task.call();
            CountDownLatch done = new CountDownLatch(1);
            Object[] result = new Object[1];
            Exception[] failure = new Exception[1];
            call(minecraft, new String[] {"execute"}, (Runnable) () -> {
                try { result[0] = task.call(); }
                catch (Exception error) { failure[0] = error; }
                finally { done.countDown(); }
            });
            if (!done.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("client thread timed out");
            if (failure[0] != null) throw failure[0];
            @SuppressWarnings("unchecked") T value = (T) result[0];
            return value;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception error) {
            SynapMcShared.error("[SynapMc] Scoreboard spoof failed: " + error);
            return null;
        }
    }

    private static final class Layers {
        Object baseline;
        final List<Handle> active = new ArrayList<Handle>();
        Layers(Object baseline) { this.baseline = baseline; }
    }

    private static final class Entry {
        final String owner;
        final int score;
        Entry(String owner, int score) { this.owner = owner; this.score = score; }
    }

    private static final class Key {
        final Object objective;
        final String owner;
        Key(Object objective, String owner) { this.objective = objective; this.owner = owner; }
        @Override public int hashCode() { return 31 * System.identityHashCode(objective) + (owner == null ? 0 : owner.hashCode()); }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Key)) return false;
            Key key = (Key) other;
            return objective == key.objective && (owner == null ? key.owner == null : owner.equals(key.owner));
        }
    }

    public static final class Handle {
        private final Key key;
        private final Object board;
        private final Object replacement;
        private volatile boolean active = true;

        private Handle(Key key, Object board, Object replacement) {
            this.key = key;
            this.board = board;
            this.replacement = replacement;
        }

        public boolean isActive() { return active; }

        public boolean revert() {
            Boolean result = onClient(() -> {
                synchronized (layers) {
                    if (!active) return false;
                    Layers state = layers.get(key);
                    if (state == null) return false;
                    int index = state.active.indexOf(this);
                    if (index < 0) return false;
                    boolean top = index == state.active.size() - 1;
                    if (top && (key.owner == null || hasEntry(board, key.objective, key.owner))) {
                        Object access = key.owner == null ? key.objective : scoreAccess(board, key.objective, key.owner);
                        if (currentValue(access, key.owner) == replacement) {
                            Object next = index == 0 ? state.baseline : state.active.get(index - 1).replacement;
                            setValue(access, key.owner, next);
                        }
                    }
                    state.active.remove(index);
                    if (state.active.isEmpty()) layers.remove(key);
                    active = false;
                    return true;
                }
            });
            return Boolean.TRUE.equals(result);
        }
    }
}
