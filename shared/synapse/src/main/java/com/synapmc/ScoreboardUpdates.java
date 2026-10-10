package com.synapmc;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/** Tracks incoming and displayed values separately, so canceled updates stay canceled across frames. */
public final class ScoreboardUpdates {
    private Object objective;
    private ScoreboardUpdate.Snapshot incoming;
    private ScoreboardUpdate.Snapshot displayed;

    public ScoreboardUpdate.Snapshot apply(Object objective, ScoreboardUpdate.Snapshot next,
            BiFunction<ScoreboardUpdate.Row, Object, ScoreboardUpdate.Row> replaceText,
            Consumer<ScoreboardUpdate> dispatch) {
        if (this.objective == objective && Objects.equals(incoming, next)) return displayed;
        ScoreboardUpdate update = new ScoreboardUpdate(next, this.objective == objective ? displayed : null, replaceText);
        try { dispatch.accept(update); }
        finally {
            this.objective = objective;
            incoming = next;
            displayed = update.finish(false);
        }
        return displayed;
    }

    public void clear() { objective = null; incoming = null; displayed = null; }
}
