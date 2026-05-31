package xyz.wagyourtail.jsmacros.synapmc.library;

import com.synapmc.SynapMcShared;
import com.synapmc.library.FDownload;
import com.synapmc.library.FInstall;
import com.synapmc.library.FPort;
import com.synapmc.library.FPrivate;
import com.synapmc.library.FRepo;
import com.synapmc.library.FTag;
import xyz.wagyourtail.jsmacros.core.Core;
import xyz.wagyourtail.jsmacros.core.library.BaseLibrary;
import xyz.wagyourtail.jsmacros.core.library.Library;
import xyz.wagyourtail.jsmacros.synapmc.config.SynapMcConfig;

import java.io.IOException;

@Library("synapmc")
public class FSynapMc extends BaseLibrary {

    private final FRepo repoHelper = new FRepo();
    private final FPort portHelper = new FPort();
    private final FTag tagHelper = new FTag();
    private final FDownload downloadHelper = new FDownload();

    public FRepo repo() {
        return repoHelper;
    }

    public FRepo repo(String repoName) {
        return new FRepo(repoName);
    }

    public FPort port() {
        return portHelper;
    }

    public FPort port(String target) {
        return new FPort(target);
    }

    public FTag tag() {
        return tagHelper;
    }

    public FDownload download() {
        return downloadHelper;
    }

    public FPrivate getPrivate() {
        return new FPrivate();
    }

    public String roaming() {
        return SynapMcShared.roamingDir.getAbsolutePath();
    }

    public SynapMcConfig getConfig() {
        return Core.getInstance().config.getOptions(SynapMcConfig.class);
    }

    public void saveConfig() {
        Core.getInstance().config.saveConfig();
    }

    public String download(String url, String savePath, boolean overwrite) {
        try {
            return SynapMcShared.downloadTo(url, savePath, overwrite);
        } catch (IOException e) {
            SynapMcShared.error("[SynapMc] download failed for " + url + ": " + e.getMessage());
            return null;
        }
    }

    public String download(String url, String savePath) {
        return download(url, savePath, true);
    }

    public FDirs getDir() {
        return new FDirs();
    }

    public static class FDirs {
        public String roaming() { return SynapMcShared.roamingDir.getAbsolutePath(); }
        public String projects() { return SynapMcShared.resolvePath("@projects").getAbsolutePath(); }
        public String library() { return SynapMcShared.resolvePath("@library").getAbsolutePath(); }
        public String macros() { return SynapMcShared.resolvePath("@macros").getAbsolutePath(); }
        public String minecraft() { return SynapMcShared.resolvePath("@minecraft").getAbsolutePath(); }
        public String mods() { return SynapMcShared.resolvePath("@mods").getAbsolutePath(); }
        public String images() { return SynapMcShared.resolvePath("@images").getAbsolutePath(); }
        public String sounds() { return SynapMcShared.resolvePath("@sounds").getAbsolutePath(); }
    }
}
