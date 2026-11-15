package com.synapmc;

import xyz.wagyourtail.jsmacros.client.api.classes.ScriptScreen;
import net.minecraft.class_4587;

public final class ScriptHistoryScreen extends ScriptScreen {
    private final ScriptHistoryUi ui;
    public ScriptHistoryScreen(Object editor) {
        super("Script History", true);
        ui = new ScriptHistoryUi(this, editor);
    }
    @Override protected void method_25426() { super.method_25426(); ui.init(); }
    @Override public void method_25394(class_4587 context, int x, int y, float delta) {
        super.method_25394(context, x, y, delta);
        ui.render(context);
    }
}
