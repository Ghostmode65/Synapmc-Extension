package com.synapmc;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public final class MacroScreenBridge {
    private static volatile boolean started;
    private static Object attachedScreen;
    private static List<Object> attachedButtons = Collections.emptyList();
    private static volatile boolean textRenderErrorLogged;
    private static final java.util.Map<Object, String> importerContexts =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<Object, String>());

    private MacroScreenBridge() {}

    public static synchronized void start() {
        if (started) return;
        started = true;
        Thread monitor = new Thread(() -> {
            while (true) {
                try {
                    Object client = client();
                    if (client != null) {
                        Method execute = client.getClass().getMethod("execute", Runnable.class);
                        execute.invoke(client, (Runnable) () -> inspect(client));
                    }
                    Thread.sleep(300);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception error) {
                    SynapMcShared.error("[SynapMc] Could not attach macro screen button: " + error);
                    return;
                }
            }
        }, "SynapMc macro screen button");
        monitor.setDaemon(true);
        monitor.start();
    }

    private static void inspect(Object client) {
        Object screen = field(client, "screen", "field_1755");
        if (!isMacroScreen(screen)) {
            attachedScreen = null;
            attachedButtons = Collections.emptyList();
            return;
        }
        if (screen == attachedScreen && children(screen).containsAll(attachedButtons)
                && !attachedButtons.isEmpty()) return;
        try {
            int width = dimension(screen, "width", "field_22789");
            int height = dimension(screen, "height", "field_22790");
            if (width == 0 || height == 0) return;
            List<Object> buttons = new ArrayList<Object>();
            int buttonWidth = Math.max(1, width / 12);
            buttons.add(addButton(screen, 0, height - 42, buttonWidth, 12,
                    "Repository", MacroScreenBridge::openRepoBrowser));
            buttons.add(addButton(screen, 0, height - 27, buttonWidth, 12,
                    "Import", () -> openImporter(screen)));
            attachedScreen = screen;
            attachedButtons = buttons;
        } catch (Exception error) {
            SynapMcShared.error("[SynapMc] Could not add macro screen button: " + error);
        }
    }

    private static boolean isMacroScreen(Object screen) {
        if (screen == null) return false;
        for (Class<?> type = screen.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().endsWith(".client.gui.screens.MacroScreen")) return true;
        }
        return false;
    }

    private static void openImporter(Object parent) {
        try {
            String packageName = parent.getClass().getPackageName();
            String screensPackage = ".client.gui.screens";
            int screensIndex = packageName.lastIndexOf(screensPackage);
            if (screensIndex < 0) throw new ClassNotFoundException("JsMacros screen package");
            String screenName = packageName.substring(0, screensIndex) + ".synapmc.SynapMcExtension";
            Object extension = Class.forName(screenName).getConstructor().newInstance();
            Object importer = extension.getClass().getMethod("createScriptDropScreen").invoke(extension);
            importerContexts.put(importer, macroScreenKind(parent));
            showChild(parent, importer);
        } catch (Exception error) {
            SynapMcShared.error("[SynapMc] Could not open script importer: " + error);
        }
    }

    public static String importerContext(Object importer) { return importerContexts.get(importer); }

    private static String macroScreenKind(Object screen) {
        String name = screen == null ? "" : screen.getClass().getSimpleName();
        if (name.equals("ServiceScreen")) return "service";
        if (name.equals("EventMacrosScreen")) return "event";
        if (name.equals("KeyMacrosScreen")) return "key";
        return "";
    }

    public static void openRepoBrowser() {
        try {
            com.synapmc.repo.RepoManager.getInstance().getDashboard().openDashboard();
        } catch (Exception error) {
            SynapMcShared.error("[SynapMc] Could not open repo browser: " + error);
        }
    }

    public static boolean showChild(Object parent, Object screen) {
        try {
            for (Method method : screen.getClass().getMethods()) {
                if (method.getName().equals("setParent") && method.getParameterCount() == 1
                        && method.getParameterTypes()[0].isInstance(parent)) {
                    method.invoke(screen, parent);
                    break;
                }
            }
            Object client = client();
            for (Method method : client.getClass().getMethods()) {
                if ((method.getName().equals("setScreen") || method.getName().equals("method_1507"))
                        && method.getParameterCount() == 1 && method.getParameterTypes()[0].isInstance(screen)) {
                    method.invoke(client, screen);
                    return true;
                }
            }
            throw new NoSuchMethodException("Minecraft setScreen");
        } catch (Exception error) {
            SynapMcShared.error("[SynapMc] Could not open script screen: " + error);
            return false;
        }
    }

    public static void onClient(Runnable action) {
        try {
            Object client = client();
            if (client != null) client.getClass().getMethod("execute", Runnable.class).invoke(client, action);
        } catch (Exception error) {
            SynapMcShared.error("[SynapMc] Could not update script screen: " + error);
        }
    }

    public static void back(Object screen) {
        try {
            screen.getClass().getMethod("openParent").invoke(screen);
        } catch (Exception error) {
            SynapMcShared.error("[SynapMc] Could not return to JsMacros: " + error);
        }
    }

    public static int dimension(Object screen, String named, String intermediary) {
        Object value = field(screen, named, intermediary);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    public static boolean scroll(Object screen, double amount) {
        return ScriptDropUi.scrollScreen(screen, amount);
    }

    public static Object addButton(Object screen, int x, int y, int width, int height,
                                   String caption, Runnable action) throws Exception {
        String prefix = screen.getClass().getName().startsWith("com.jsmacrosce.")
                ? "com.jsmacrosce.wagyourgui" : "xyz.wagyourtail.wagyourgui";
        Class<?> buttonClass = Class.forName(prefix + ".elements.Button");
        Object font = field(screen, "font", "field_22793");
        if (font == null) throw new NoSuchFieldException("Minecraft font");
        Constructor<?> constructor = null;
        for (Constructor<?> candidate : buttonClass.getConstructors()) {
            if (candidate.getParameterCount() == 11) { constructor = candidate; break; }
        }
        if (constructor == null) throw new NoSuchMethodException("JsMacros Button constructor");
        Object button = constructor.newInstance(x, y, width, height, font, 0, -16777216,
                Integer.MAX_VALUE, -1, literal(caption), (Consumer<Object>) ignored -> action.run());
        return addWidget(screen, button);
    }

    public static Object addTextInput(Object screen, int x, int y, int width, int height,
                                      Consumer<String> onChange) throws Exception {
        String prefix = screen.getClass().getName().startsWith("com.jsmacrosce.")
                ? "com.jsmacrosce.wagyourgui" : "xyz.wagyourtail.wagyourgui";
        Object font = field(screen, "font", "field_22793");
        if (font == null) throw new NoSuchFieldException("Minecraft font");
        Class<?> inputClass = Class.forName(prefix + ".elements.TextInput");
        for (Constructor<?> constructor : inputClass.getConstructors()) {
            if (constructor.getParameterCount() != 12) continue;
            Object input = constructor.newInstance(x, y, width, height, font, 0, -16777216,
                    Integer.MAX_VALUE, -1, "", (Consumer<Object>) ignored -> { }, onChange);
            return addWidget(screen, input);
        }
        throw new NoSuchMethodException("JsMacros TextInput constructor");
    }

    /** A normal accessible/clickable widget, rendered as left-aligned text only. */
    public static Object addTextButton(Object screen, int x, int y, int width, int height,
                                       String caption, Runnable action) throws Exception {
        Object button = addButton(screen, x, y, width, height, caption, action);
        for (String setter : new String[] {"setColor", "setBorderColor", "setHighlightColor"})
            button.getClass().getMethod(setter, int.class).invoke(button, 0);
        button.getClass().getField("horizCenter").setBoolean(button, false);
        return button;
    }

    public static String inputValue(Object input) {
        try { return (String) input.getClass().getField("content").get(input); }
        catch (Exception ignored) { return ""; }
    }

    public static boolean inputFocused(Object input) {
        for (String name : new String[] {"isFocused", "method_25370"}) {
            try { return (Boolean) input.getClass().getMethod(name).invoke(input); }
            catch (Exception ignored) { }
        }
        return true;
    }

    public static void inputText(Object input, String value) {
        try {
            if (!value.equals(input.getClass().getField("content").get(input))) {
                input.getClass().getMethod("setMessage", String.class).invoke(input, value);
                input.getClass().getMethod("updateSelStart", int.class).invoke(input, value.length());
                input.getClass().getMethod("updateSelEnd", int.class).invoke(input, value.length());
                Field cursor = input.getClass().getDeclaredField("arrowCursor");
                cursor.setAccessible(true);
                cursor.setInt(input, value.length());
            }
        } catch (Exception error) {
            SynapMcShared.error("[SynapMc] Could not update rename input: " + error);
        }
    }

    public static void focusInput(Object screen, Object input) {
        try {
            input.getClass().getMethod("setSelected", boolean.class).invoke(input, true);
            for (Method method : screen.getClass().getMethods()) {
                if ((method.getName().equals("setFocused") || method.getName().equals("method_25395"))
                        && method.getParameterCount() == 1 && method.getParameterTypes()[0].isInstance(input)) {
                    method.invoke(screen, input);
                    return;
                }
            }
        } catch (Exception error) {
            SynapMcShared.error("[SynapMc] Could not focus rename input: " + error);
        }
    }

    public static void textColor(Object widget, int color) {
        if (widget == null) return;
        try { widget.getClass().getMethod("setTextColor", int.class).invoke(widget, color); }
        catch (Exception error) { SynapMcShared.error("[SynapMc] Could not color script row: " + error); }
    }

    private static Object addWidget(Object screen, Object widget) throws Exception {
        for (Method method : screen.getClass().getMethods()) {
            if ((method.getName().equals("addDrawableChild") || method.getName().equals("method_37063"))
                    && method.getParameterCount() == 1 && method.getParameterTypes()[0].isInstance(widget)) {
                method.invoke(screen, widget);
                return widget;
            }
        }
        throw new NoSuchMethodException("Minecraft addDrawableChild");
    }

    public static void setTooltip(Object button, String message) {
        if (button == null) return;
        try {
            Object label = literal(message);
            for (Method method : button.getClass().getMethods()) {
                if (!(method.getName().equals("setTooltip") || method.getName().equals("method_47399"))
                        || method.getParameterCount() != 1) continue;
                Class<?> type = method.getParameterTypes()[0];
                if (type.isInstance(label)) {
                    method.invoke(button, label);
                    return;
                }
                for (Method factory : type.getMethods()) {
                    if (java.lang.reflect.Modifier.isStatic(factory.getModifiers())
                            && type.isAssignableFrom(factory.getReturnType()) && factory.getParameterCount() == 1
                            && factory.getParameterTypes()[0].isInstance(label)) {
                        method.invoke(button, factory.invoke(null, label));
                        return;
                    }
                }
            }
        } catch (Exception ignored) { }
    }

    public static void drawText(Object context, Object screen, String value, int x, int y, int color) {
        if (context == null || screen == null) return;
        try {
            Object font = field(screen, "font", "field_22793");
            Object label = literal(value);
            int width = value.length() * 6;
            for (String methodName : new String[] {"width", "method_1727"}) {
                try {
                    Object measured = font.getClass().getMethod(methodName, String.class).invoke(font, value);
                    if (measured instanceof Number) width = ((Number) measured).intValue();
                    break;
                } catch (NoSuchMethodException ignored) { }
            }
            for (Method method : context.getClass().getMethods()) {
                if ((method.getName().equals("centeredText") || method.getName().equals("method_27534"))
                        && method.getParameterCount() == 5
                        && method.getParameterTypes()[0].isInstance(font)
                        && method.getParameterTypes()[1].isInstance(label)) {
                    method.invoke(context, font, label, x + width / 2, y, color);
                    return;
                }
            }
            for (Class<?> type = screen.getClass(); type != null; type = type.getSuperclass()) {
                for (Method method : type.getDeclaredMethods()) {
                    if (method.getName().equals("method_27534") && method.getParameterCount() == 6
                            && method.getParameterTypes()[0].isInstance(context)
                            && method.getParameterTypes()[1].isInstance(font)
                            && method.getParameterTypes()[2].isInstance(label)) {
                        method.setAccessible(true);
                        method.invoke(null, context, font, label, x + width / 2, y, color);
                        return;
                    }
                }
            }
            throw new NoSuchMethodException("Minecraft centered text renderer");
        } catch (Exception error) {
            if (!textRenderErrorLogged) {
                textRenderErrorLogged = true;
                SynapMcShared.error("[SynapMc] Could not draw script screen text: " + error);
            }
        }
    }

    public static void caption(Object button, String value) {
        if (button == null) return;
        try {
            Object text = literal(value);
            for (Method method : button.getClass().getMethods()) {
                if ((method.getName().equals("setMessage") || method.getName().equals("method_25355"))
                        && method.getParameterCount() == 1 && method.getParameterTypes()[0].isInstance(text)) {
                    method.invoke(button, text);
                    return;
                }
            }
        } catch (Exception error) {
            SynapMcShared.error("[SynapMc] Could not update button: " + error);
        }
    }

    public static void visible(Object button, boolean visible) {
        if (button == null) return;
        for (String name : new String[] {"visible", "field_22764"}) {
            try {
                button.getClass().getField(name).setBoolean(button, visible);
                return;
            } catch (ReflectiveOperationException ignored) { }
        }
    }

    private static Object literal(String value) throws Exception {
        try {
            return Class.forName("net.minecraft.network.chat.Component").getMethod("literal", String.class)
                    .invoke(null, value);
        } catch (ClassNotFoundException ignored) {
            return Class.forName("net.minecraft.class_2561").getMethod("method_43470", String.class)
                    .invoke(null, value);
        }
    }

    private static Object client() {
        for (String[] mapping : new String[][] {
                {"net.minecraft.client.Minecraft", "getInstance"},
                {"net.minecraft.client.MinecraftClient", "getInstance"},
                {"net.minecraft.class_310", "method_1551"}
        }) {
            try { return Class.forName(mapping[0]).getMethod(mapping[1]).invoke(null); }
            catch (Exception ignored) { }
        }
        return null;
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

    private static List<?> children(Object screen) {
        try {
            Object value = screen.getClass().getMethod("children").invoke(screen);
            if (value instanceof List) return (List<?>) value;
        } catch (Exception ignored) { }
        try {
            Object value = screen.getClass().getMethod("method_25396").invoke(screen);
            if (value instanceof List) return (List<?>) value;
        } catch (Exception ignored) { }
        Object value = field(screen, "children", "field_22786");
        return value instanceof List ? (List<?>) value : java.util.Collections.emptyList();
    }
}
