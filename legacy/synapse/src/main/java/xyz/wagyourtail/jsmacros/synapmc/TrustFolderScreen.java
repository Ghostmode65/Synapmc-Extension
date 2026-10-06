package xyz.wagyourtail.jsmacros.synapmc;

import net.minecraft.class_4587;

import com.synapmc.TrustFolderUi;
import xyz.wagyourtail.jsmacros.client.api.classes.ScriptScreen;
import java.nio.file.Path;

public final class TrustFolderScreen extends ScriptScreen {
    private final TrustFolderUi ui;

    public TrustFolderScreen(Path folder, Runnable onTrust, Runnable onCancel) {
        super("Trust Script Folder", true);
        ui = new TrustFolderUi(this, folder, onTrust, onCancel);
    }

    @Override public void method_25394(class_4587 context, int mouseX, int mouseY, float tickDelta) {
        super.method_25394(context, mouseX, mouseY, tickDelta);
        ui.render(context);
    }

    @Override protected void method_25426() {
        super.method_25426();
        ui.init();
    }

    @Override public void method_25432() {
        ui.onRemoved();
        super.method_25432();
    }
}
