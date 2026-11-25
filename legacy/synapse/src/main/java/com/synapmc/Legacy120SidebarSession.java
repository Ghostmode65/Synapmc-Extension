package com.synapmc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.class_2561;
import net.minecraft.class_266;
import net.minecraft.class_267;
import net.minecraft.class_268;
import net.minecraft.class_269;

/** Adapts the 1.20.1 sidebar without changing server scores, teams, or objectives. */
public final class Legacy120SidebarSession {
    private record Entry(String owner, int score) { }
    private final ScoreboardUpdates updates = new ScoreboardUpdates();
    private class_2561 title;
    private Collection<class_267> scores = List.of();

    public static List<ScoreboardUpdate.Row> rows(class_269 board, class_266 objective) {
        // Erase the newer compile stub's element type before creating lambdas;
        // otherwise their signatures would reference a class absent in 1.20.1.
        return ((Collection<?>) board.method_1184(objective)).stream().map(value -> (class_267) value)
                .filter(score -> score.method_1129() != null && !score.method_1129().startsWith("#"))
                .sorted(class_267.field_1413.reversed()).limit(15)
                .map(score -> new ScoreboardUpdate.Row(class_268.method_1142(board.method_1164(score.method_1129()),
                        class_2561.method_43470(score.method_1129())), new Entry(score.method_1129(), score.method_1126())))
                .toList();
    }

    public void prepare(class_266 objective) {
        class_269 board = objective.method_1117();
        var incoming = new ScoreboardUpdate.Snapshot(objective.method_1114().method_27661(), rows(board, objective));
        var displayed = updates.apply(objective, incoming, (row, text) -> new ScoreboardUpdate.Row(
                ((class_2561) text).method_27661(), row == null ? new Entry("", 0) : row.nativeEntry()), update -> {
            ICoreAccess core = SynapMcShared.core;
            if (core == null) return;
            try { core.triggerScoreboardEvent(update); }
            catch (Throwable error) { SynapMcShared.error("[SynapMc] Scoreboard callback failed: " + error); }
        });
        title = (class_2561) displayed.title();
        List<class_267> result = new ArrayList<>();
        // The native renderer draws its collection from bottom to top.
        for (int i = displayed.rows().size() - 1; i >= 0; i--) {
            var row = displayed.rows().get(i);
            result.add(new DisplayScore(board, objective, (Entry) row.nativeEntry(), (class_2561) row.text()));
        }
        scores = List.copyOf(result);
    }

    public Collection<class_267> scores() { return scores; }
    public class_2561 title() { return title; }
    public class_2561 text(class_267 score) { return ((DisplayScore) score).text; }
    public void clear() { updates.clear(); title = null; scores = List.of(); }

    private static final class DisplayScore extends class_267 {
        private final Entry entry;
        private final class_2561 text;
        private DisplayScore(class_269 board, class_266 objective, Entry entry, class_2561 text) {
            super(board, objective, entry.owner()); this.entry = entry; this.text = text;
        }
        @Override public String method_1129() { return entry.owner(); }
        @Override public int method_1126() { return entry.score(); }
    }
}
