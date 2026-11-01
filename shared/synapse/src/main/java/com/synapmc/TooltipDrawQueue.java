package com.synapmc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;


public final class TooltipDrawQueue {
    private final Predicate<Object> isElement;
    private final List<Object> elements = new ArrayList<>();
    private final Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean open = true;

    public TooltipDrawQueue(Predicate<Object> isElement) { this.isElement = isElement; }

    public synchronized void draw(Object elementOrList) {
        if (!open) throw new IllegalStateException("Tooltip has finished; use a joined listener to draw in this frame");
        List<Object> requested = new ArrayList<>();
        if (elementOrList instanceof Iterable<?> list) {
            for (Object element : list) requested.add(element);
        } else if (elementOrList instanceof Object[] array) {
            Collections.addAll(requested, array);
        } else {
            requested.add(elementOrList);
        }

        for (Object element : requested) if (element == null || !isElement.test(element))
            throw new IllegalArgumentException("draw() expects a JsMacros render element or a list/array of render elements");
        for (Object element : requested) if (seen.add(element)) elements.add(element);
    }

    public synchronized List<Object> finish(Comparator<Object> order) {
        if (!open) return List.of();
        open = false;
        List<Object> result = new ArrayList<>(elements);
        elements.clear();
        seen.clear();
        result.sort(order);
        return result;
    }

    public synchronized void draw2D(Object drawing) {
        if (!open) throw new IllegalStateException("Tooltip has finished; use a joined listener to draw in this frame");
        Class<?> type = drawing == null ? null : drawing.getClass();
        while (type != null && !Set.of(
                "com.jsmacrosce.jsmacros.client.api.classes.render.Draw2D",
                "xyz.wagyourtail.jsmacros.client.api.classes.render.Draw2D",
                "xyz.wagyourtail.jsmacros.client.api.classes.Draw2D").contains(type.getName())) {
            type = type.getSuperclass();
        }
        if (type == null) throw new IllegalArgumentException("setOnInt() expects a JsMacros Draw2D");
        try {
            try {
                if (!(boolean) type.getMethod("isVisible").invoke(drawing)) return;
            } catch (NoSuchMethodException olderCore) { }
            draw(type.getMethod("getElements").invoke(drawing));
        } catch (ReflectiveOperationException error) {
            throw new IllegalArgumentException("Could not read the Draw2D elements", error);
        }
    }
}
