package com.synapmc;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import @TEXT_TYPE@;

/** Adapts Minecraft's private, immutable sidebar display records without modifying server data. */
public final class SidebarRenderSession {
    private final ScoreboardUpdates updates = new ScoreboardUpdates();
    private Constructor<?> constructor;
    private Method name;
    private Method score;
    private Method scoreWidth;
    private @TEXT_SIMPLE@ title;

    public Object[] apply(Object objective, @TEXT_SIMPLE@ incomingTitle, Object[] entries,
            IntFunction<Object[]> factory) {
        try {
            Class<?> rowType = entries.getClass().getComponentType();
            if (constructor == null) {
                // The record is private in all supported Minecraft releases. Cache its accessors once.
                constructor = rowType.getDeclaredConstructor(@TEXT_SIMPLE@.class, @TEXT_SIMPLE@.class, int.class);
                name = rowType.getDeclaredMethod("@ROW_NAME@");
                score = rowType.getDeclaredMethod("@ROW_SCORE@");
                scoreWidth = rowType.getDeclaredMethod("@ROW_WIDTH@");
                constructor.setAccessible(true);
                name.setAccessible(true);
                score.setAccessible(true);
                scoreWidth.setAccessible(true);
            }
            List<ScoreboardUpdate.Row> rows = new ArrayList<>();
            for (Object row : entries) rows.add(new ScoreboardUpdate.Row(name.invoke(row), row));
            var incoming = new ScoreboardUpdate.Snapshot(incomingTitle.@TEXT_COPY@(), rows);
            var displayed = updates.apply(objective, incoming, this::replaceText, update -> {
                ICoreAccess core = SynapMcShared.core;
                if (core == null) return;
                try { core.triggerScoreboardEvent(update); }
                catch (Throwable error) { SynapMcShared.error("[SynapMc] Scoreboard callback failed: " + error); }
            });
            title = (@TEXT_SIMPLE@) displayed.title();
            Object[] result = factory.apply(displayed.rows().size());
            for (int i = 0; i < result.length; i++) result[i] = displayed.rows().get(i).nativeEntry();
            return result;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not adapt Minecraft sidebar display entries", error);
        }
    }

    private ScoreboardUpdate.Row replaceText(ScoreboardUpdate.Row row, Object text) {
        try {
            Object entry = constructor.newInstance(text, row == null ? @TEXT_SIMPLE@.@TEXT_LITERAL@("") : score.invoke(row.nativeEntry()),
                row == null ? 0 : scoreWidth.invoke(row.nativeEntry()));
            return new ScoreboardUpdate.Row(text, entry);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    public @TEXT_SIMPLE@ title() { return title; }
    public void clear() { updates.clear(); title = null; }
}
