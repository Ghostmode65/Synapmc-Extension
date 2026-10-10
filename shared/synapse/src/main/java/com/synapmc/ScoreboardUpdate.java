package com.synapmc;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/** One sidebar update. Mutations are accepted only while joined listeners are running. */
public final class ScoreboardUpdate {
    public record Row(Object text, Object nativeEntry) {}
    public record Snapshot(Object title, List<Row> rows) {
        public Snapshot { rows = List.copyOf(rows); }
    }

    private final Snapshot previous;
    private final Row[] rows = new Row[15];
    private final BiFunction<Row, Object, Row> replaceText;
    private Object title;
    private boolean open = true;

    public ScoreboardUpdate(Snapshot incoming, Snapshot previous, BiFunction<Row, Object, Row> replaceText) {
        this.previous = previous;
        this.replaceText = replaceText;
        title = incoming.title();
        for (int i = 0; i < incoming.rows().size(); i++) rows[i] = incoming.rows().get(i);
    }

    public synchronized Object getText(int line) {
        checkLine(line);
        return line == 0 ? title : rows[line - 1] == null ? null : rows[line - 1].text();
    }

    public synchronized void setText(int line, Object text) {
        requireOpen();
        checkLine(line);
        if (text == null) throw new IllegalArgumentException("Scoreboard text cannot be null");
        if (line == 0) title = text;
        else {
            Row row = rows[line - 1];
            if (row == null) throw new IllegalArgumentException("No scoreboard row at line " + line);
            rows[line - 1] = replaceText.apply(row, text);
        }
    }

    public synchronized void addLine(int line, Object text) {
        requireOpen();
        checkRow(line);
        if (text == null) throw new IllegalArgumentException("Scoreboard text cannot be null");
        if (line > 1 && rows[line - 2] == null) throw new IllegalArgumentException("Cannot insert after a missing scoreboard row");
        Row added = replaceText.apply(null, text);
        for (int i = 14; i >= line; i--) rows[i] = rows[i - 1];
        rows[line - 1] = added;
    }

    public synchronized void removeLine(int line) {
        requireOpen();
        checkRow(line);
        if (rows[line - 1] == null) return;
        for (int i = line - 1; i < 14; i++) rows[i] = rows[i + 1];
        rows[14] = null;
    }

    /** null means all lines; a number, array, or Iterable selects individual lines. */
    public synchronized void cancel(Object lines) {
        requireOpen();
        List<Integer> selected = new ArrayList<>();
        if (lines == null) for (int i = 0; i <= 15; i++) selected.add(i);
        else if (lines instanceof Iterable<?> list) for (Object line : list) selected.add(number(line));
        else if (lines instanceof Object[] list) for (Object line : list) selected.add(number(line));
        else selected.add(number(lines));
        // Validate the entire selection before changing any line.
        for (int line : selected) checkLine(line);
        for (int line : selected) {
            if (line == 0) title = previous == null ? null : previous.title();
            else rows[line - 1] = previous == null || line > previous.rows().size()
                ? null : previous.rows().get(line - 1);
        }
    }

    public synchronized Snapshot finish(boolean cancelAll) {
        requireOpen();
        if (cancelAll) cancel(null);
        open = false;
        List<Row> result = new ArrayList<>();
        for (Row row : rows) if (row != null) result.add(row);
        return new Snapshot(title, result);
    }

    private static int number(Object value) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != number.intValue())
            throw new IllegalArgumentException("Scoreboard lines must be integer numbers from 0 to 15");
        return number.intValue();
    }
    private static void checkLine(int line) {
        if (line < 0 || line > 15) throw new IllegalArgumentException("Scoreboard line must be from 0 to 15");
    }
    private static void checkRow(int line) {
        if (line < 1 || line > 15) throw new IllegalArgumentException("Sidebar rows must be from 1 to 15; use setText(0, text) for the title");
    }
    private void requireOpen() {
        if (!open) throw new IllegalStateException("Scoreboard update has finished; use a joined listener to edit it");
    }
}
