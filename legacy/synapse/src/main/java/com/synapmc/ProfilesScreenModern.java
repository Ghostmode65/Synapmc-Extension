package com.synapmc;

import xyz.wagyourtail.jsmacros.client.api.classes.render.ScriptScreen;
import net.minecraft.class_332;

public final class ProfilesScreenModern extends ScriptScreen {
    private final ProfilesUi ui;
    public ProfilesScreenModern() {
        super("Profiles", true);
        ui = new ProfilesUi(this);
    }
    @Override protected void method_25426() { super.method_25426(); ui.init(); }
    @Override public void method_25394(class_332 context, int x, int y, float delta) {
        super.method_25394(context, x, y, delta);
        ui.render(context);
    }
    @Override public boolean method_25401(double x, double y, double horizontal, double vertical) {
        return ui.scroll(vertical) || super.method_25401(x, y, horizontal, vertical);
    }
    @Override public boolean method_25404(int key, int scanCode, int modifiers) {
        return ui.keyPressed(key) || super.method_25404(key, scanCode, modifiers);
    }

}
