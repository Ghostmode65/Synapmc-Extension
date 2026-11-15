package com.synapmc;

import xyz.wagyourtail.jsmacros.client.api.classes.render.ScriptScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class ScriptHistoryScreen extends ScriptScreen {
    private final ScriptHistoryUi ui;
    public ScriptHistoryScreen(Object editor) {
        super("Script History", true);
        ui = new ScriptHistoryUi(this, editor);
    }
    @Override protected void init() { super.init(); ui.init(); }
    @Override public void extractRenderState(GuiGraphicsExtractor context, int x, int y, float delta) {
        super.extractRenderState(context, x, y, delta);
        ui.render(context);
    }
}
