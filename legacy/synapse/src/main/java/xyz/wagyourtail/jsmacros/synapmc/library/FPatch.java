package xyz.wagyourtail.jsmacros.synapmc.library;

import com.synapmc.ScoreboardSpoof;
import com.synapmc.SynapMcShared;
import xyz.wagyourtail.jsmacros.client.api.helpers.TextHelper;

import java.lang.reflect.Constructor;

public final class FPatch {
    public ScoreboardPatch scoreboard(int line) {
        return line < -1 ? null : new ScoreboardPatch(line);
    }

    public static final class ScoreboardPatch {
        private final int line;
        private ScoreboardSpoof.Handle activePatch;

        private ScoreboardPatch(int line) { this.line = line; }

        public TextHelper getText() {
            Object text = ScoreboardSpoof.readText(line);
            if (text == null) return null;
            // Select the native-text constructor without a dependency on Minecraft mappings.
            for (Constructor<?> constructor : TextHelper.class.getConstructors()) {
                if (constructor.getParameterCount() != 1 || !constructor.getParameterTypes()[0].isInstance(text)) continue;
                try {
                    return (TextHelper) constructor.newInstance(text);
                } catch (ReflectiveOperationException error) {
                    SynapMcShared.error("[SynapMc] Could not wrap scoreboard text: " + error);
                    return null;
                }
            }
            SynapMcShared.error("[SynapMc] No TextHelper constructor accepts the scoreboard text");
            return null;
        }

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
