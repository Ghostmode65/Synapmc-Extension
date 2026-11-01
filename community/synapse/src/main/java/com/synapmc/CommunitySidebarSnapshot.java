package com.synapmc;

import static com.synapmc.NativeTextureAccess.*;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Comparator;
import java.util.function.IntFunction;


public final class CommunitySidebarSnapshot {
    private Method rowMapper;
    private IntFunction<Object[]> factory;

    public void prepare(Object hud, Object objective) {
        if (SynapMcShared.core == null) return;
        try {
            if (rowMapper == null) {
                rowMapper = type("net.minecraft.class_329").getDeclaredMethod("method_55439",
                    type("net.minecraft.class_269"), type("net.minecraft.class_9022"), type("net.minecraft.class_9011"));
                rowMapper.setAccessible(true);
                Class<?> rowType = rowMapper.getReturnType();
                factory = size -> (Object[]) Array.newInstance(rowType, size);
            }
            Object board = call(objective, new String[] {"method_1117"});
            Object format = call(objective, new String[] {"method_55380"}, field(type("net.minecraft.class_9025"), "field_47567"));
            @SuppressWarnings("unchecked")
            Comparator<Object> order = (Comparator<Object>) field(hud, "field_47550");
            Collection<?> scores = (Collection<?>) call(board, new String[] {"method_1184"}, objective);
            Object[] rows = scores.stream()
                .filter(entry -> !((Boolean) call(entry, new String[] {"method_55385"})))
                .sorted(order).limit(15)
                .map(entry -> row(hud, board, format, entry)).toArray(factory);
            ((SidebarAccess) hud).synapmc$prepareSidebar(objective,
                call(objective, new String[] {"method_1114"}), rows, factory);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not prepare the Community sidebar event", error);
        }
    }

    private Object row(Object hud, Object board, Object format, Object entry) {
        try { return rowMapper.invoke(hud, board, format, entry); }
        catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not map the Community sidebar row",
                error instanceof InvocationTargetException call ? call.getCause() : error);
        }
    }
}
