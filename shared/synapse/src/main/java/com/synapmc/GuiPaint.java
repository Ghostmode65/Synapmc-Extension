package com.synapmc;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GuiPaint {
    private static final Map<Class<?>, Method> fills = new ConcurrentHashMap<>();
    private GuiPaint() {}

    public static void fill(Object context, Object screen, int x1, int y1, int x2, int y2, int color) {
        try {
            Method fill = fills.get(context.getClass());
            if (fill == null) {
                for (Method method : context.getClass().getMethods()) {
                    if ((method.getName().equals("fill") || method.getName().equals("method_25294"))
                            && java.util.Arrays.equals(method.getParameterTypes(), new Class<?>[] {int.class, int.class, int.class, int.class, int.class})) {
                        fill = method;
                        break;
                    }
                }
                if (fill == null) for (Class<?> type = screen.getClass(); type != null && fill == null; type = type.getSuperclass()) {
                    for (Method method : type.getDeclaredMethods()) {
                        if (method.getName().equals("method_25294") && Modifier.isStatic(method.getModifiers())
                                && method.getParameterCount() == 6 && method.getParameterTypes()[0].isInstance(context)) {
                            method.setAccessible(true);
                            fill = method;
                            break;
                        }
                    }
                }
                if (fill == null) throw new NoSuchMethodException("Minecraft GUI fill");
                fills.put(context.getClass(), fill);
            }
            if (fill.getParameterCount() == 5) fill.invoke(context, x1, y1, x2, y2, color);
            else fill.invoke(null, context, x1, y1, x2, y2, color);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Could not draw color picker", error); }
    }
}
