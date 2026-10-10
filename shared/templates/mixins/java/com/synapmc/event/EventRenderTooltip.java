package com.synapmc.event;

import com.synapmc.LoreWindow;
import com.synapmc.TooltipDrawQueue;
import @CORE_PACKAGE@.core.Core;
import @CORE_PACKAGE@.core.event.BaseEvent;
import @CORE_PACKAGE@.core.event.Event;

/** Fired after tooltip layout, before native drawing. Queued elements are drawn after the tooltip. */
@Event(value = "renderTooltip"@EVENT_JOINABLE@)
public final class EventRenderTooltip extends BaseEvent {
    public final LoreWindow element;
    private final TooltipDrawQueue drawings;

    public EventRenderTooltip(Core<?, ?> core, LoreWindow element, TooltipDrawQueue drawings) {
        @EVENT_SUPER@
        this.element = element;
        this.drawings = drawings;
    }

    @Override public boolean joinable() { return true; }
    public void draw(Object elementOrList) { drawings.draw(elementOrList); }
    /** Also lets languages such as Lua coerce a sequential table into a Java array. */
    public void draw(Object[] elements) { drawings.draw(elements); }
}
