package xyz.wagyourtail.jsmacros.synapmc;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import com.synapmc.TrustFolderUi;
import xyz.wagyourtail.jsmacros.client.api.classes.render.ScriptScreen;
import java.nio.file.Path;

public final class TrustFolderScreen extends ScriptScreen {
    private final TrustFolderUi ui;

    public TrustFolderScreen(Path folder, Runnable onTrust, Runnable onCancel) {
        super("Trust Script Folder", true);
        ui = new TrustFolderUi(this, folder, onTrust, onCancel);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float tickDelta) {
        super.extractRenderState(context, mouseX, mouseY, tickDelta);
        ui.render(context);
    }

    @Override protected void init() {
        super.init();
        ui.init();
    }

    @Override public void removed() {
        ui.onRemoved();
        super.removed();
    }
}
