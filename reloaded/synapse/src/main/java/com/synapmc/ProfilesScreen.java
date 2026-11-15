package com.synapmc;

import xyz.wagyourtail.jsmacros.client.api.classes.render.ScriptScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class ProfilesScreen extends ScriptScreen {
    private final ProfilesUi ui;
    public ProfilesScreen() {
        super("Profiles", true);
        ui = new ProfilesUi(this);
    }
    @Override protected void init() { super.init(); ui.init(); }
    @Override public void extractRenderState(GuiGraphicsExtractor context, int x, int y, float delta) {
        super.extractRenderState(context, x, y, delta);
        ui.render(context);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return ui.scroll(vertical) || super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        return ui.keyPressed(event.key()) || super.keyPressed(event);
    }

}
