package com.synapmc.event;

import com.synapmc.LoreWindow;
import com.synapmc.TooltipDrawQueue;
import @CORE_PACKAGE@.core.Core;
import @CORE_PACKAGE@.core.event.BaseEvent;
import @CORE_PACKAGE@.core.event.Event;


@Event(value = "Tooltip"@EVENT_JOINABLE@)
public final class EventTooltip extends BaseEvent {
    public final LoreWindow element;
    private final TooltipDrawQueue drawings;

    public EventTooltip(Core<?, ?> core, LoreWindow element, TooltipDrawQueue drawings) {
        @EVENT_SUPER@
        this.element = element;
        this.drawings = drawings;
    }

    @Override public boolean joinable() { return true; }
    public int getX() { return element.getX(); }
    public int getY() { return element.getY(); }
    public int getWidth() { return element.getWidth(); }
    public int getHeight() { return element.getHeight(); }
    public void draw(Object elementOrList) { drawings.draw(elementOrList); }

    public void draw(Object[] elements) { drawings.draw(elements); }
    public void setOnInt(Object draw2d) { drawings.draw2D(draw2d); }
}
