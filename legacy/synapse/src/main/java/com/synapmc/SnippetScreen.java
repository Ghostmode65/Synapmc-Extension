package com.synapmc;

import xyz.wagyourtail.jsmacros.client.api.classes.ScriptScreen;
import net.minecraft.class_4587;
import java.util.function.Consumer;

public final class SnippetScreen extends ScriptScreen {
    private final SnippetUi ui;
    public SnippetScreen(String extension, Consumer<String> insert) {
        super("Snippets", true);
        ui = new SnippetUi(this, extension, insert);
    }
    @Override protected void method_25426() { super.method_25426(); ui.init(); }
    @Override public void method_25394(class_4587 context, int x, int y, float delta) {
        super.method_25394(context, x, y, delta);
        ui.render(context);
    }
    @Override public boolean method_25401(double x, double y, double vertical) {
        return ui.scroll(vertical) || super.method_25401(x, y, vertical);
    }
}
