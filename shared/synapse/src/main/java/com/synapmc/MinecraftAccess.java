package com.synapmc;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class MinecraftAccess {
    private MinecraftAccess() {}

    public static File gameDirectory() {
        Object client = client();
        Object directory = field(client, "gameDirectory", "runDirectory", "field_1697");
        return directory instanceof File ? (File) directory : new File(System.getProperty("user.dir"));
    }

    public static Map<String, Object> clientInfo() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        Object version = invokeStatic("net.minecraft.SharedConstants", "getGameVersion");
        if (version == null) version = invokeStatic("net.minecraft.class_155", "method_16673");
        Object versionName = invoke(version, "getName", "method_48019");
        result.put("version", versionName == null ? "unknown" : versionName.toString());
        Object session = invoke(client(), "getSession", "method_1548");
        Object name = invoke(session, "getUsername", "method_1676");
        Object uuid = invoke(session, "getUuid", "method_44717");
        if (name != null) result.put("name", name.toString());
        if (uuid != null) result.put("uuid", uuid.toString());
        return result;
    }

    public static boolean isFullscreen() {
        Object minecraft = client();
        return minecraft != null && onClientThread(minecraft, () -> {
            Object window = invoke(minecraft, "getWindow", "method_22683");
            return Boolean.TRUE.equals(invoke(window, "isFullscreen", "method_4498"));
        });
    }

    public static boolean toggleFullscreen(boolean enabled) {
        Object minecraft = client();
        if (minecraft == null) {
            SynapMcShared.error("[SynapMc] Minecraft client is unavailable");
            return false;
        }
        return onClientThread(minecraft, () -> setFullscreen(minecraft, enabled));
    }

    private static boolean onClientThread(Object minecraft, BooleanSupplier action) {
        if (Boolean.TRUE.equals(invoke(minecraft, "isOnThread", "method_18854"))) {
            return action.getAsBoolean();
        }
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean result = new AtomicBoolean(false);
        Runnable change = () -> {
            try {
                result.set(action.getAsBoolean());
            } catch (RuntimeException e) {
                SynapMcShared.error("[SynapMc] Fullscreen operation failed: " + e.getMessage());
            } finally {
                done.countDown();
            }
        };
        try {
            minecraft.getClass().getMethod("execute", Runnable.class).invoke(minecraft, change);
            if (!done.await(5, TimeUnit.SECONDS)) {
                SynapMcShared.error("[SynapMc] Timed out reading or changing fullscreen mode");
                return false;
            }
            return result.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            SynapMcShared.error("[SynapMc] Could not access fullscreen mode: " + e.getMessage());
            return false;
        }
    }

    private static boolean setFullscreen(Object minecraft, boolean enabled) {
        Object window = invoke(minecraft, "getWindow", "method_22683");
        if (window == null) {
            SynapMcShared.error("[SynapMc] Minecraft window is unavailable");
            return false;
        }
        Object current = invoke(window, "isFullscreen", "method_4498");
        if (!(current instanceof Boolean)) {
            SynapMcShared.error("[SynapMc] Could not read fullscreen mode");
            return false;
        }
        if (((Boolean) current) == enabled) return true;

        Object options = field(minecraft, "options", "field_1690");
        Object fullscreenOption = invoke(options, "getFullscreen", "method_42447");
        if (fullscreenOption != null) {
            invokeWithValue(fullscreenOption, enabled, "setValue", "method_41748");
        }
        if (Boolean.TRUE.equals(invoke(window, "isFullscreen", "method_4498")) != enabled
                && !invokeVoid(window, "toggleFullscreen", "method_4500")) {
            SynapMcShared.error("[SynapMc] Minecraft window cannot toggle fullscreen");
            return false;
        }

        boolean changed = Boolean.TRUE.equals(invoke(window, "isFullscreen", "method_4498")) == enabled;
        if (changed && fullscreenOption != null) {
            Object saved = invoke(fullscreenOption, "getValue", "method_41753");
            if (!Boolean.valueOf(enabled).equals(saved)) {
                invokeWithValue(fullscreenOption, enabled, "setValue", "method_41748");
            }
            invokeVoid(options, "write", "method_1640");
        }
        return changed;
    }

    public static File screenshot(String filename) throws Exception {
        Object client = client();
        if (client == null) throw new IOException("Minecraft client is unavailable");
        if (Boolean.TRUE.equals(invoke(client, "isOnThread", "method_18854"))) {
            throw new IOException("Screenshot needs a script or worker thread so the Minecraft client can finish the capture");
        }
        File directory = new File(SynapMcShared.roamingDir, "downloads/screenshots");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Could not create screenshot directory");
        File file = new File(directory, filename);
        CountDownLatch done = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];
        Runnable task = () -> {
            try {
                Object framebuffer = invoke(client, "getFramebuffer", "method_1522");
                if (framebuffer == null) throw new IOException("Minecraft framebuffer is unavailable");
                Class<?> recorder = Class.forName("net.minecraft.client.util.ScreenshotRecorder");
                for (Method method : recorder.getMethods()) {
                    if (!method.getName().equals("saveScreenshot") || method.getParameterCount() != 4) continue;
                    Class<?>[] parameters = method.getParameterTypes();
                    if (parameters[0] == File.class && parameters[1] == String.class
                            && Consumer.class.isAssignableFrom(parameters[3])) {
                        method.invoke(null, directory, filename, framebuffer, (Consumer<Object>) ignored -> done.countDown());
                        return;
                    }
                }
                throw new NoSuchMethodException("ScreenshotRecorder.saveScreenshot");
            } catch (Throwable error) {
                failure[0] = error;
                done.countDown();
            }
        };
        client.getClass().getMethod("execute", Runnable.class).invoke(client, task);
        if (!done.await(10, TimeUnit.SECONDS)) throw new IOException("Screenshot timed out");
        if (failure[0] != null) throw new IOException("Screenshot failed", failure[0]);
        if (!file.isFile()) throw new IOException("Screenshot file was not created");
        return file;
    }

    public static void notifyPlayer(String message) {
        Object client = client();
        if (client == null) return;
        try {
            Runnable task = () -> {
                try {
                    Object hud = invoke(client, "getInGameHud");
                    if (hud == null) hud = field(client, "inGameHud", "field_1705");
                    Object chat = invoke(hud, "getChatHud", "method_1743");
                    Class<?> text = Class.forName("net.minecraft.text.Text");
                    Object literal = text.getMethod("literal", String.class).invoke(null, "[SynapMc] " + message);
                    chat.getClass().getMethod("addMessage", text).invoke(chat, literal);
                } catch (Exception ignored) {
                }
            };
            if (Boolean.TRUE.equals(invoke(client, "isOnThread"))) task.run();
            else client.getClass().getMethod("execute", Runnable.class).invoke(client, task);
        } catch (Exception ignored) {
        }
    }

    public static void notifyExecutedScript(String clientName, String language, String script) {
        Object client = client();
        if (client == null) return;
        try {
            Runnable task = () -> {
                try {
                    Class<?> textClass = textClass();
                    Object message = literal(textClass, "[SynapMc] "
                            + (clientName == null || clientName.trim().isEmpty() ? "TCP client" : clientName)
                            + " is executing a " + language + " script. ");
                    Object copy = literal(textClass, "[Copy script]");
                    Object style = copyStyle(textClass, script);
                    if (style != null) copy = setStyle(copy, style);
                    message = append(message, copy);
                    Object hud = invoke(client, "getInGameHud");
                    if (hud == null) hud = field(client, "inGameHud", "field_1705");
                    Object chat = invoke(hud, "getChatHud", "method_1743");
                    for (Method method : chat.getClass().getMethods()) {
                        if ((method.getName().equals("addMessage") || method.getName().equals("method_1740"))
                                && method.getParameterCount() == 1
                                && method.getParameterTypes()[0].isInstance(message)) {
                            method.invoke(chat, message);
                            return;
                        }
                    }
                } catch (Exception ignored) { }
            };
            if (Boolean.TRUE.equals(invoke(client, "isOnThread", "method_18854"))) task.run();
            else client.getClass().getMethod("execute", Runnable.class).invoke(client, task);
        } catch (Exception ignored) { }
    }

    private static Class<?> textClass() throws ClassNotFoundException {
        for (String name : new String[] {"net.minecraft.text.Text", "net.minecraft.network.chat.Component", "net.minecraft.class_2561"}) {
            try { return Class.forName(name); } catch (ClassNotFoundException ignored) { }
        }
        throw new ClassNotFoundException("Minecraft text component");
    }

    private static Object literal(Class<?> textClass, String value) throws Exception {
        for (String method : new String[] {"literal", "of", "method_43470"}) {
            try { return textClass.getMethod(method, String.class).invoke(null, value); }
            catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException("literal text factory");
    }

    private static Object append(Object text, Object extra) throws Exception {
        for (Method method : text.getClass().getMethods()) {
            if (method.getParameterCount() == 1 && method.getParameterTypes()[0].isInstance(extra)
                    && text.getClass().isAssignableFrom(method.getReturnType())) {
                Object result = method.invoke(text, extra);
                return result == null ? text : result;
            }
        }
        return text;
    }

    private static Object copyStyle(Class<?> textClass, String script) {
        String base = textClass.getPackage().getName();
        for (String clickName : new String[] {base + ".ClickEvent", "net.minecraft.class_2558"}) {
            try {
                Class<?> clickType = Class.forName(clickName);
                Object click = makeCopyClick(clickType, script);
                if (click == null) continue;
                for (String styleName : new String[] {base + ".Style", "net.minecraft.class_2583"}) {
                    try {
                        Class<?> styleClass = Class.forName(styleName);
                        Object style = emptyStyle(styleClass);
                        if (style == null) continue;
                        for (Method method : styleClass.getMethods()) {
                            if (method.getParameterCount() == 1 && method.getParameterTypes()[0].isInstance(click)
                                    && styleClass.isAssignableFrom(method.getReturnType())) {
                                return method.invoke(style, click);
                            }
                        }
                    } catch (Exception ignored) { }
                }
            } catch (Exception ignored) { }
        }
        return null;
    }

    private static Object makeCopyClick(Class<?> clickType, String script) {
        for (Method method : clickType.getMethods()) {
            if (java.lang.reflect.Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == String.class && clickType.isAssignableFrom(method.getReturnType())) {
                try {
                    if (method.getName().toLowerCase().contains("copy")) return method.invoke(null, script);
                } catch (Exception ignored) { }
            }
        }
        for (Class<?> nested : clickType.getDeclaredClasses()) {
            if (!nested.getSimpleName().toLowerCase().contains("copy")) continue;
            for (Constructor<?> constructor : nested.getDeclaredConstructors()) {
                if (constructor.getParameterCount() == 1 && constructor.getParameterTypes()[0] == String.class) {
                    try {
                        constructor.setAccessible(true);
                        return constructor.newInstance(script);
                    } catch (Exception ignored) { }
                }
            }
        }
        for (Constructor<?> constructor : clickType.getConstructors()) {
            Class<?>[] params = constructor.getParameterTypes();
            if (params.length != 2 || params[1] != String.class || !params[0].isEnum()) continue;
            for (Object action : params[0].getEnumConstants()) {
                if (!"COPY_TO_CLIPBOARD".equals(((Enum<?>) action).name())) continue;
                try { return constructor.newInstance(action, script); } catch (Exception ignored) { }
            }
        }
        return null;
    }

    private static Object emptyStyle(Class<?> styleClass) {
        try { return styleClass.getField("EMPTY").get(null); } catch (Exception ignored) { }
        for (Field field : styleClass.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers()) || !styleClass.isAssignableFrom(field.getType())) continue;
            try { field.setAccessible(true); return field.get(null); } catch (Exception ignored) { }
        }
        return null;
    }

    private static Object setStyle(Object text, Object style) {
        for (Method method : text.getClass().getMethods()) {
            if (method.getParameterCount() == 1 && method.getParameterTypes()[0].isInstance(style)) {
                try {
                    Object result = method.invoke(text, style);
                    return result == null ? text : result;
                } catch (Exception ignored) { }
            }
        }
        return text;
    }

    public static boolean openEditor(File file, int line) {
        return openEditor(file, line, null);
    }

    public static boolean openEditor(File file, int line, Object parent) {
        for (String name : new String[] {
                "com.jsmacrosce.jsmacros.client.gui.screens.EditorScreen",
                "xyz.wagyourtail.jsmacros.client.gui.screens.EditorScreen"
        }) {
            try {
                Class<?> editor = Class.forName(name);
                if (parent != null) {
                    for (java.lang.reflect.Constructor<?> constructor : editor.getConstructors()) {
                        Class<?>[] parameters = constructor.getParameterTypes();
                        if (parameters.length == 2 && parameters[0].isInstance(parent) && parameters[1] == File.class) {
                            return MacroScreenBridge.showChild(parent, constructor.newInstance(parent, file));
                        }
                    }
                    throw new NoSuchMethodException("EditorScreen parent constructor");
                }
                editor.getMethod("openAndScrollToLine", File.class, int.class, int.class, int.class)
                        .invoke(null, file, line, 0, -1);
                return true;
            } catch (ClassNotFoundException ignored) {
            } catch (Exception e) {
                SynapMcShared.error("[SynapMc] Could not open editor: " + e.getMessage());
                return false;
            }
        }
        SynapMcShared.error("[SynapMc] EditorScreen is unavailable");
        return false;
    }

    private static Object client() {
        Object current = invokeStatic("net.minecraft.client.Minecraft", "getInstance");
        if (current != null) return current;
        Object value = invokeStatic("net.minecraft.client.MinecraftClient", "getInstance");
        return value != null ? value : invokeStatic("net.minecraft.class_310", "method_1551");
    }

    private static Object invokeStatic(String className, String method) {
        try {
            return Class.forName(className).getMethod(method).invoke(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object invoke(Object target, String... names) {
        if (target == null) return null;
        for (String name : names) {
            try {
                return target.getClass().getMethod(name).invoke(target);
            } catch (Exception ignored) { }
        }
        return null;
    }

    private static boolean invokeVoid(Object target, String... names) {
        if (target == null) return false;
        for (String name : names) {
            try {
                target.getClass().getMethod(name).invoke(target);
                return true;
            } catch (NoSuchMethodException ignored) { }
            catch (Exception e) {
                SynapMcShared.error("[SynapMc] " + name + " failed: " + e.getMessage());
                return false;
            }
        }
        return false;
    }

    private static boolean invokeWithValue(Object target, Object value, String... names) {
        if (target == null) return false;
        for (String name : names) {
            try {
                target.getClass().getMethod(name, Object.class).invoke(target, value);
                return true;
            } catch (NoSuchMethodException ignored) { }
            catch (Exception e) {
                SynapMcShared.error("[SynapMc] " + name + " failed: " + e.getMessage());
                return false;
            }
        }
        return false;
    }

    private static Object field(Object target, String... names) {
        if (target == null) return null;
        for (String name : names) {
            try {
                Field field = target.getClass().getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (Exception ignored) { }
        }
        return null;
    }
}
