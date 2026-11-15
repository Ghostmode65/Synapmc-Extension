package com.synapmc;

import xyz.wagyourtail.jsmacros.client.api.classes.ScriptScreen;
import net.minecraft.class_4587;

public final class SynapMcScreen extends ScriptScreen {
    private final SynapMcHubUi ui;
    public SynapMcScreen() {
        super("SynapMc", false);
        ui = new SynapMcHubUi(this);
    }
    @Override protected void method_25426() { super.method_25426(); ui.init(); }
    @Override public void method_25394(class_4587 context, int x, int y, float delta) {
        super.method_25394(context, x, y, delta);
        ui.render(context);
    }
    // Legacy ScriptScreen always calls this background hook before its widgets.
    public void method_25433(class_4587 context, int offset) { ui.renderBackground(context); }
    @Override public boolean method_25404(int key, int scanCode, int modifiers) {
        return ui.keyPressed(key) || super.method_25404(key, scanCode, modifiers);
    }
}
