package com.synapmc;

import @CORE_PACKAGE@.client.api.classes.render.components.RenderElement;
import @DRAW_CONTEXT@;

/** One native tooltip invocation, including its callback and drawing phase. */
public final class TooltipRenderSession {
    private final @DRAW_CONTEXT_SIMPLE@ context;
    private final int mouseX, mouseY;
    private final TooltipDrawQueue drawings = new TooltipDrawQueue(value -> value instanceof RenderElement);
    private boolean started;

    public TooltipRenderSession(Object context, int mouseX, int mouseY) {
        this.context = (@DRAW_CONTEXT_SIMPLE@) context;
        this.mouseX = mouseX; this.mouseY = mouseY;
    }

    public void begin(LoreWindow window) {
        if (started) return;
        started = true;
        ICoreAccess core = SynapMcShared.core;
        if (core == null) return;
        try { core.triggerTooltipEvent(window, drawings); }
        catch (Throwable error) { SynapMcShared.error("[SynapMc] renderTooltip callback failed: " + error); }
    }

    public void finish() {
        var elements = drawings.finish(java.util.Comparator.comparingInt(value -> ((RenderElement) value).getZIndex()));
        if (elements.isEmpty()) return;
        @TOOLTIP_LAYER_BEGIN@
        try {
            for (Object element : elements) {
                try { ((RenderElement) element).@ELEMENT_RENDER_METHOD@(context, mouseX, mouseY, 0); }
                catch (Throwable error) { SynapMcShared.error("[SynapMc] Could not draw tooltip element: " + error); }
            }
        } finally {
            @TOOLTIP_LAYER_END@
        }
    }
}
