package com.synapmc;

import xyz.wagyourtail.jsmacros.client.api.classes.render.ScriptScreen;
import net.minecraft.class_332;
import java.util.function.Consumer;

public final class ColorPickerScreenModern extends ScriptScreen {
    private final ColorPickerUi ui;
    public ColorPickerScreenModern(String literal, Consumer<String> apply) {
        super("Color Picker", true);
        ui = new ColorPickerUi(this, literal, apply);
    }
    @Override protected void method_25426() { super.method_25426(); ui.init(); }
    @Override public void method_25394(class_332 context, int x, int y, float delta) {
        super.method_25394(context, x, y, delta);
        ui.render(context);
    }
    @Override public boolean method_25402(double x, double y, int button) {
        return ui.click(x, y, button) || super.method_25402(x, y, button);
    }
    @Override public boolean method_25403(double x, double y, int button, double dx, double dy) {
        return ui.drag(x, y) || super.method_25403(x, y, button, dx, dy);
    }
    @Override public boolean method_25406(double x, double y, int button) {
        ui.release();
        return super.method_25406(x, y, button);
    }
    @Override public boolean method_25404(int key, int scanCode, int modifiers) {
        return ui.keyPressed(key) || super.method_25404(key, scanCode, modifiers);
    }
}
