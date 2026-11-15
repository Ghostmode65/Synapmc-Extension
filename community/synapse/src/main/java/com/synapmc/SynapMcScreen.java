package com.synapmc;

import com.jsmacrosce.jsmacros.client.api.classes.render.ScriptScreen;
import net.minecraft.class_332;

public final class SynapMcScreen extends ScriptScreen {
    private final SynapMcHubUi ui;
    public SynapMcScreen() {
        super("SynapMc", false);
        drawTitle = false;
        ui = new SynapMcHubUi(this);
    }
    @Override protected void method_25426() { super.method_25426(); ui.init(); }
    @Override public void method_25394(class_332 context, int x, int y, float delta) {
        ui.renderBackground(context);
        super.method_25394(context, x, y, delta);
        ui.render(context);
    }
    @Override public boolean method_25404(int key, int scanCode, int modifiers) {
        return ui.keyPressed(key) || super.method_25404(key, scanCode, modifiers);
    }
}
