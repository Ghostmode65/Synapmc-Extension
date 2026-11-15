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
    private static Object repositoryButton;
    private static final String[] REPOSITORY_ICONS = {"🌏", "🌎", "🌍"};
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
        try {
            Object screen = currentScreen(client);
            if (!isMacroScreen(screen)) {
                attachedScreen = null;
                attachedButtons = Collections.emptyList();
                repositoryButton = null;
                return;
            }
            if (screen == attachedScreen && children(screen).containsAll(attachedButtons)
                    && !attachedButtons.isEmpty()) {
                updateRepositoryIcon(System.nanoTime());
                return;
            }
            int width = dimension(screen, "width", "field_22789");
            int height = dimension(screen, "height", "field_22790");
            if (width == 0 || height == 0) return;
            List<Object> buttons = new ArrayList<Object>();
            int profileX = width * (field(screen, "serviceScreen") == null ? 2 : 3) / 6 + 3;
            int switchWidth = 22;
            Object profiles = headerButton(screen, profileX, switchWidth, "⇄", () -> openProfiles(screen));
            lowerSwapSymbol(profiles);
            setTooltip(profiles, "Manage and switch JsMacros profiles");
            buttons.add(profiles);
            Object snippets = headerButton(screen, profileX + switchWidth, switchWidth, "✀",
                    () -> EditorSnippets.open(screen, null, null));
            setTooltip(snippets, "Snippets");
            buttons.add(snippets);
            Object importer = headerButton(screen, profileX + 2 * switchWidth, switchWidth, "⬇",
                    () -> openImporter(screen));
            setTooltip(importer, "Import");
            buttons.add(importer);
            repositoryButton = headerButton(screen, profileX + 3 * switchWidth, switchWidth,
                    REPOSITORY_ICONS[0], MacroScreenBridge::openRepoBrowser);
            setTooltip(repositoryButton, "Repository browser");
            buttons.add(repositoryButton);
            attachedScreen = screen;
            attachedButtons = buttons;
            updateRepositoryIcon(System.nanoTime());
        } catch (Exception error) {
            SynapMcShared.error("[SynapMc] Could not add macro screen button: " + error);
        }
    }

    private static void updateRepositoryIcon(long nanos) throws Exception {
        String icon = REPOSITORY_ICONS[(int) Math.floorMod(nanos / 1_000_000_000L, REPOSITORY_ICONS.length)];
        if (repositoryButton != null && !icon.equals(widgetCaption(repositoryButton))) caption(repositoryButton, icon);
    }

    private static void lowerSwapSymbol(Object button) throws Exception {
        // Reset the native text layout before adjusting it, so repairing or resizing
        // the screen never accumulates additional offsets.
        caption(button, "⇄");
        for (Class<?> type = button.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field center = type.getDeclaredField("verticalCenter");
                center.setAccessible(true);
                center.setInt(button, center.getInt(button) + 2);
                return;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException("JsMacros Button.verticalCenter");
    }

    /** Keeps native tab separators without adding dividers between the shortcuts. */
    public static void fillHeader(Object screen, Object context, int x1, int y1, int x2, int y2, int color) {
        GuiPaint.fill(context, screen, x1, y1, x2, y2, color);
    }

    private static Object headerButton(Object screen, int x, int width, String caption, Runnable action) throws Exception {
        for (Object widget : children(screen)) {
            String existing = widgetCaption(widget);
            if (!existing.equals(caption) && !(caption.equals(REPOSITORY_ICONS[0])
                    && java.util.Arrays.asList(REPOSITORY_ICONS).contains(existing))) continue;
            widget.getClass().getField("onPress").set(widget, (Consumer<Object>) ignored -> action.run());
            position(widget, x, 0, width, 20);
            return widget;
        }
        return addButton(screen, x, 0, width, 20, caption, action);
    }

    private static Object currentScreen(Object client) throws Exception {
        Object gui = field(client, "gui");
        if (gui != null) {
            try { return gui.getClass().getMethod("screen").invoke(gui); }
            catch (NoSuchMethodException ignored) { }
        }
        return field(client, "screen", "field_1755");
    }

    private static boolean isMacroScreen(Object screen) {
        if (screen == null) return false;
        for (Class<?> type = screen.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().endsWith(".client.gui.screens.MacroScreen")) return true;
        }
        return false;
    }

    public static void openImporter(Object parent) {
        try {
            Object importer;
            if (SynapMcShared.core != null) importer = SynapMcShared.core.createScriptDropScreen();
            else {
                Object extension = Class.forName(corePrefix(parent) + ".synapmc.SynapMcExtension").getConstructor().newInstance();
                importer = extension.getClass().getMethod("createScriptDropScreen").invoke(extension);
            }
            importerContexts.put(importer, "key");
            showChild(parent, importer);
        } catch (Exception error) {
            SynapMcShared.error("[SynapMc] Could not open script importer: " + error);
        }
    }

    public static String importerContext(Object importer) { return importerContexts.get(importer); }

    public static void openHub(Object parent) {
        try {
            showChild(parent, Class.forName(screenVariant("com.synapmc.SynapMcScreen", parent)).getConstructor().newInstance());
        } catch (Exception error) { SynapMcShared.error("[SynapMc] Could not open SynapMc: " + error); }
    }

    public static void openProfiles(Object parent) {
        try {
            showChild(parent, Class.forName(screenVariant("com.synapmc.ProfilesScreen", parent)).getConstructor().newInstance());
        } catch (Exception error) { SynapMcShared.error("[SynapMc] Could not open profiles: " + error); }
    }

    public static boolean openProfileKeys(Object parent) {
        try {
            Class<?> type = Class.forName(corePrefix(parent) + ".client.gui.screens.KeyMacrosScreen");
            for (Constructor<?> constructor : type.getConstructors())
                if (constructor.getParameterCount() == 1 && constructor.getParameterTypes()[0].isInstance(parent))
                    return showChild(parent, constructor.newInstance(parent));
            throw new NoSuchMethodException("JsMacros Keys screen");
        } catch (Exception error) { SynapMcShared.error("[SynapMc] Could not edit profile bindings: " + error); return false; }
    }

    private static String screenVariant(String name, Object parent) {
        if (SynapMcShared.core != null && "legacy".equals(SynapMcShared.core.getJsVersion()))
            for (Method method : parent.getClass().getMethods())
                if (method.getName().equals("method_25394") && method.getParameterCount() == 4
                        && method.getParameterTypes()[0].getName().equals("net.minecraft.class_332")) return name + "Modern";
        return name;
    }

    public static void openCoreSettings(Object screen) {
        try {
            int width = dimension(screen, "width", "field_22789");
            int height = dimension(screen, "height", "field_22790");
            createOverlay(screen, Class.forName(corePrefix(screen) + ".client.gui.settings.SettingsOverlay"),
                    width / 8, height / 8, width * 3 / 4, height * 3 / 4,
                    field(screen, "font", "field_22793"), screen);
        } catch (Exception error) { SynapMcShared.error("[SynapMc] Could not open JsMacros profiles/settings: " + error); }
    }

    private static String corePrefix(Object screen) {
        return guiPrefix(screen).substring(0, guiPrefix(screen).indexOf(".wagyourgui")) + ".jsmacros";
    }

    private static String widgetCaption(Object widget) {
        Object label = field(widget, "message", "field_22754");
        for (String name : new String[] {"getMessage", "method_25369"}) {
            try { label = widget.getClass().getMethod(name).invoke(widget); break; }
            catch (ReflectiveOperationException ignored) { }
        }
        if (label == null) return "";
        // Translation keys identify the native button even in a non-English locale.
        for (String name : new String[] {"getContents", "method_10851"}) {
            try {
                Object contents = label.getClass().getMethod(name).invoke(label);
                for (String key : new String[] {"getKey", "method_11022"}) {
                    try { return (String) contents.getClass().getMethod(key).invoke(contents); }
                    catch (ReflectiveOperationException ignored) { }
                }
            } catch (ReflectiveOperationException ignored) { }
        }
        try { return (String) label.getClass().getMethod("getString").invoke(label); }
        catch (ReflectiveOperationException ignored) { return ""; }
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
            for (Object owner : new Object[] {field(client, "gui"), client}) {
                if (owner == null) continue;
                for (Method method : owner.getClass().getMethods()) {
                    if ((method.getName().equals("setScreen") || method.getName().equals("method_1507"))
                            && method.getParameterCount() == 1 && method.getParameterTypes()[0].isInstance(screen)) {
                        method.invoke(owner, screen);
                        return true;
                    }
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
        String prefix = guiPrefix(screen);
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
        String prefix = guiPrefix(screen);
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

    public static boolean hasOverlay(Object screen) { return field(screen, "overlay") != null; }

    public static void choiceMenu(Object screen, int x, int y, List<String> labels, Consumer<Integer> choice) throws Exception {
        int menuWidth = 92;
        int menuHeight = labels.size() * 12 + 4;
        x = Math.max(2, Math.min(x, dimension(screen, "width", "field_22789") - menuWidth - 2));
        y = Math.max(2, Math.min(y, dimension(screen, "height", "field_22790") - menuHeight - 2));
        List<Object> text = new ArrayList<>();
        for (String label : labels) text.add(literal(label));
        openOverlay(screen, "SelectorDropdownOverlay", x, y, menuWidth, menuHeight,
                text, field(screen, "font", "field_22793"), screen, choice);
    }

    public static void textPrompt(Object screen, String message, String initial, Consumer<String> accept) throws Exception {
        int w = Math.min(240, dimension(screen, "width", "field_22789") - 16);
        int h = Math.min(100, dimension(screen, "height", "field_22790") - 16);
        openOverlay(screen, "TextPrompt", (dimension(screen, "width", "field_22789") - w) / 2,
                (dimension(screen, "height", "field_22790") - h) / 2, w, h,
                field(screen, "font", "field_22793"), literal(message), initial, screen, accept);
    }

    public static void confirm(Object screen, String message, Runnable accept) throws Exception {
        int w = Math.min(240, dimension(screen, "width", "field_22789") - 16);
        int h = Math.min(100, dimension(screen, "height", "field_22790") - 16);
        openOverlay(screen, "ConfirmOverlay", (dimension(screen, "width", "field_22789") - w) / 2,
                (dimension(screen, "height", "field_22790") - h) / 2, w, h,
                field(screen, "font", "field_22793"), literal(message), screen, (Consumer<Object>) ignored -> accept.run());
    }

    private static void openOverlay(Object screen, String name, Object... args) throws Exception {
        Class<?> type = Class.forName(guiPrefix(screen) + ".overlays." + name);
        createOverlay(screen, type, args);
    }

    private static void createOverlay(Object screen, Class<?> type, Object... args) throws Exception {
        for (Constructor<?> constructor : type.getConstructors()) {
            if (constructor.getParameterCount() != args.length) continue;
            Object overlay = constructor.newInstance(args);
            for (Method method : screen.getClass().getMethods())
                if (method.getName().equals("openOverlay") && method.getParameterCount() == 1
                        && method.getParameterTypes()[0].isInstance(overlay)) {
                    method.invoke(screen, overlay);
                    return;
                }
        }
        throw new NoSuchMethodException("JsMacros " + type.getSimpleName() + " overlay");
    }

    private static String guiPrefix(Object screen) {
        for (Class<?> type = screen.getClass(); type != null; type = type.getSuperclass())
            if (type.getName().startsWith("com.jsmacrosce.")) return "com.jsmacrosce.wagyourgui";
        return "xyz.wagyourtail.wagyourgui";
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

    public static void blurInput(Object input) {
        try { input.getClass().getMethod("setSelected", boolean.class).invoke(input, false); }
        catch (ReflectiveOperationException error) { SynapMcShared.error("[SynapMc] Could not unfocus input: " + error); }
    }

    public static void textColor(Object widget, int color) {
        if (widget == null) return;
        try { widget.getClass().getMethod("setTextColor", int.class).invoke(widget, color); }
        catch (Exception error) { SynapMcShared.error("[SynapMc] Could not color script row: " + error); }
    }

    public static void removeWidget(Object screen, Object widget) {
        for (Class<?> type = screen.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods())
                if ((method.getName().equals("removeWidget") || method.getName().equals("method_37066"))
                        && method.getParameterCount() == 1 && method.getParameterTypes()[0].isInstance(widget)) {
                    try { method.setAccessible(true); method.invoke(screen, widget); return; }
                    catch (ReflectiveOperationException ignored) { }
                }
        }
        children(screen).remove(widget);
    }

    public static void buttonColor(Object button, int color) {
        try { button.getClass().getMethod("setColor", int.class).invoke(button, color); }
        catch (ReflectiveOperationException ignored) { }
    }

    public static void position(Object widget, int x, int y, int width, int height) {
        try { widget.getClass().getMethod("setPos", int.class, int.class, int.class, int.class).invoke(widget, x, y, width, height); }
        catch (ReflectiveOperationException error) { SynapMcShared.error("[SynapMc] Could not position widget: " + error); }
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

    public static int textWidth(Object screen, String value) {
        Object font = field(screen, "font", "field_22793");
        if (font != null) for (String methodName : new String[] {"width", "method_1727"}) {
            try { return ((Number) font.getClass().getMethod(methodName, String.class).invoke(font, value)).intValue(); }
            catch (ReflectiveOperationException ignored) { }
        }
        return value.length() * 6;
    }

    public static void drawText(Object context, Object screen, String value, int x, int y, int color) {
        if (context == null || screen == null) return;
        try {
            Object font = field(screen, "font", "field_22793");
            Object label = literal(value);
            int width = textWidth(screen, value);
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

    public static void active(Object button, boolean active) {
        if (button == null) return;
        for (String name : new String[] {"active", "field_22763"}) {
            try { button.getClass().getField(name).setBoolean(button, active); return; }
            catch (ReflectiveOperationException ignored) { }
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
