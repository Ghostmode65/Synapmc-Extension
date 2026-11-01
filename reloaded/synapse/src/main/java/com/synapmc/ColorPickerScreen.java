package com.synapmc;

import xyz.wagyourtail.jsmacros.client.api.classes.render.ScriptScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.function.Consumer;

public final class ColorPickerScreen extends ScriptScreen {
    private final ColorPickerUi ui;
    public ColorPickerScreen(String literal, Consumer<String> apply) {
        super("Color Picker", true);
        ui = new ColorPickerUi(this, literal, apply);
    }
    @Override protected void init() { super.init(); ui.init(); }
    @Override public void extractRenderState(GuiGraphicsExtractor context, int x, int y, float delta) {
        super.extractRenderState(context, x, y, delta);
        ui.render(context);
    }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        return ui.click(event.x(), event.y(), event.button()) || super.mouseClicked(event, doubleClick);
    }
    @Override public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        return ui.drag(event.x(), event.y()) || super.mouseDragged(event, dx, dy);
    }
    @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        ui.release();
        return super.mouseReleased(event);
    }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        return ui.keyPressed(event.key()) || super.keyPressed(event);
    }
}
