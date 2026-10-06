package xyz.wagyourtail.jsmacros.synapmc.library;

import com.synapmc.ScoreboardSpoof;

public final class FPatch {
    public ScoreboardPatch scoreboard(int line) {
        return line < -1 ? null : new ScoreboardPatch(line);
    }

    public static final class ScoreboardPatch {
        private final int line;
        private ScoreboardSpoof.Handle activePatch;

        private ScoreboardPatch(int line) { this.line = line; }

        public String getText() { return ScoreboardSpoof.readText(line); }

        public boolean setText(String text) {
            if (text == null) return false;
            if (activePatch != null) activePatch.revert();
            activePatch = ScoreboardSpoof.change(line, text);
            return activePatch != null;
        }

        public boolean revert() {
            if (activePatch == null) return false;
            boolean reverted = activePatch.revert();
            activePatch = null;
            return reverted;
        }

        public boolean isActive() { return activePatch != null && activePatch.isActive(); }
    }
}
