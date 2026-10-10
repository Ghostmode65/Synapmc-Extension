package xyz.wagyourtail.jsmacros.synapmc;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import xyz.wagyourtail.jsmacros.client.api.classes.render.ScriptScreen;
import com.synapmc.ScriptDropUi;

import java.nio.file.Path;
import java.util.List;

public final class ScriptDropScreen extends ScriptScreen implements ScriptDropUi.ScriptDropScreenAccess {
    private final ScriptDropUi ui = new ScriptDropUi(this);

    public ScriptDropScreen() { super("Import Scripts", true); }

    @Override public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float tickDelta) {
        super.extractRenderState(context, mouseX, mouseY, tickDelta);
        ui.render(context);
    }

    @Override protected void init() {
        super.init();
        ui.init();
    }

    @Override public void onFilesDrop(List<Path> paths) { ui.drop(paths); }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (vertical != 0) { ui.scroll(vertical); return true; }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        return ui.keyPressed(event.key()) || super.keyPressed(event);
    }
    @Override public void scroll(double amount) { ui.scroll(amount); }
}
