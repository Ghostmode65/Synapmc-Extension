package xyz.wagyourtail.jsmacros.synapmc;

import net.minecraft.class_4587;

import com.synapmc.ScriptDropUi;
import xyz.wagyourtail.jsmacros.client.api.classes.ScriptScreen;

import java.nio.file.Path;
import java.util.List;

public final class ScriptDropScreen extends ScriptScreen implements ScriptDropUi.ScriptDropScreenAccess {
    private final ScriptDropUi ui = new ScriptDropUi(this);

    public ScriptDropScreen() { super("Import Scripts", true); }

    @Override public void method_25394(class_4587 context, int mouseX, int mouseY, float tickDelta) {
        super.method_25394(context, mouseX, mouseY, tickDelta);
        ui.render(context);
    }

    @Override protected void method_25426() {
        super.method_25426();
        ui.init();
    }

    @Override public void method_29638(List<Path> paths) { ui.drop(paths); }
    @Override public boolean method_25401(double mouseX, double mouseY, double vertical) {
        if (vertical != 0) { ui.scroll(vertical); return true; }
        return super.method_25401(mouseX, mouseY, vertical);
    }
    @Override public void scroll(double amount) { ui.scroll(amount); }
}
