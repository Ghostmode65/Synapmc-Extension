package com.synapmc.event;

import com.synapmc.ScoreboardUpdate;
import @CORE_PACKAGE@.core.Core;
import @CORE_PACKAGE@.core.event.BaseEvent;
import @CORE_PACKAGE@.core.event.Event;
import @CORE_PACKAGE@.client.api.@HELPER_PACKAGE@.TextHelper;
import @TEXT_TYPE@;

/** Fired when the visible sidebar changes, before Minecraft measures and draws it. */
@Event(value = "Scoreboard", cancellable = true@EVENT_JOINABLE@)
public final class EventScoreboard extends BaseEvent {
    private final ScoreboardUpdate update;

    public EventScoreboard(Core<?, ?> core, ScoreboardUpdate update) {
        @EVENT_SUPER@
        this.update = update;
    }

    @Override public boolean joinable() { return true; }
    public void cancel(Object lineOrList) { update.cancel(lineOrList); }
    /** Enables sequential Lua tables to coerce to a Java array. */
    public void cancel(Object[] lines) { update.cancel(lines); }
    public TextHelper getText(int line) {
        @TEXT_SIMPLE@ text = (@TEXT_SIMPLE@) update.getText(line);
        return text == null ? null : TextHelper.wrap(text.@TEXT_COPY@());
    }
    public void setText(int line, Object value) {
        update.setText(line, text(value));
    }
    public void addLine(int line, Object value) { update.addLine(line, text(value)); }
    public void removeLine(int line) { update.removeLine(line); }
    private static @TEXT_SIMPLE@ text(Object value) {
        @TEXT_SIMPLE@ text;
        if (value instanceof String string) text = @TEXT_SIMPLE@.@TEXT_LITERAL@(string);
        else if (value instanceof TextHelper helper) text = helper.getRaw();
        else if (value instanceof @TEXT_SIMPLE@ component) text = component;
        else throw new IllegalArgumentException("Scoreboard text must be a string or TextHelper");
        return text.@TEXT_COPY@();
    }
}
