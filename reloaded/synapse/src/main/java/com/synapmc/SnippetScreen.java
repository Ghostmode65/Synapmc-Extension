package com.synapmc;

import xyz.wagyourtail.jsmacros.client.api.classes.render.ScriptScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.function.Consumer;

public final class SnippetScreen extends ScriptScreen {
    private final SnippetUi ui;
    public SnippetScreen(String extension, Consumer<String> insert) {
        super("Snippets", true);
        ui = new SnippetUi(this, extension, insert);
    }
    @Override protected void init() { super.init(); ui.init(); }
    @Override public void extractRenderState(GuiGraphicsExtractor context, int x, int y, float delta) {
        super.extractRenderState(context, x, y, delta);
        ui.render(context);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return ui.scroll(vertical) || super.mouseScrolled(x, y, horizontal, vertical);
    }
}
