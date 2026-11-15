package com.synapmc;

import xyz.wagyourtail.jsmacros.client.api.classes.render.ScriptScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class SynapMcScreen extends ScriptScreen {
    private final SynapMcHubUi ui;
    public SynapMcScreen() {
        super("SynapMc", false);
        drawTitle = false;
        ui = new SynapMcHubUi(this);
    }
    @Override protected void init() { super.init(); ui.init(); }
    @Override public void extractRenderState(GuiGraphicsExtractor context, int x, int y, float delta) {
        ui.renderBackground(context);
        super.extractRenderState(context, x, y, delta);
        ui.render(context);
    }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        return ui.keyPressed(event.key()) || super.keyPressed(event);
    }
}
