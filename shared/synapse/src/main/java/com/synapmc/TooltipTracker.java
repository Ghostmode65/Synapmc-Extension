package com.synapmc;

/** Publishes immutable tooltip bounds without depending on a Minecraft mapping or JsMacros edition. */
public final class TooltipTracker {
    private record State(Object current, Object previous, boolean rendering, boolean captured) {}
    private static volatile State state = new State(null, null, false, false);
    private TooltipTracker() {}

    public static void beginFrame() {
        State old = state;
        state = new State(null, old.current, true, false);
    }

    public static void capture(Object window) {
        State old = state;
        state = new State(window, old.previous, old.rendering, true);
    }

    public static void endFrame() {
        state = new State(state.current, null, false, true);
    }

    public static void clear() { state = new State(null, null, false, false); }

    /** Early HUD rendering precedes inventory tooltips, so it uses the last completed frame. */
    public static Object get() {
        State snapshot = state;
        return snapshot.rendering && !snapshot.captured ? snapshot.previous : snapshot.current;
    }
}
