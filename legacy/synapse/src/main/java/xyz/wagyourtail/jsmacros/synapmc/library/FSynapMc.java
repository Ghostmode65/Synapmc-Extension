package xyz.wagyourtail.jsmacros.synapmc.library;

import xyz.wagyourtail.jsmacros.core.library.Library;
import xyz.wagyourtail.jsmacros.core.library.PerExecLibrary;
import xyz.wagyourtail.jsmacros.core.language.BaseScriptContext;
import xyz.wagyourtail.jsmacros.core.Core;
import com.synapmc.library.FDownload;
import com.synapmc.library.FEditor;
import com.synapmc.library.FPort;
import com.synapmc.library.FSynapse;
import com.synapmc.library.FTag;

import java.io.File;
import java.util.Map;

@Library("synapmc")
public final class FSynapMc extends PerExecLibrary {
    private final FSynapse service;
    private final FEditor editor = new FEditor();

    public FSynapMc(BaseScriptContext<?> ctx) {
        super(ctx);
        service = new FSynapse(Core.getInstance(), ctx);
    }

    public boolean openEditor(String file) { return editor.open(file); }
    public boolean openEditor(String file, int line) { return editor.open(file, line); }
    public boolean openEditor(File file) { return editor.open(file); }
    public boolean openEditor(File file, int line) { return editor.open(file, line); }

    public String roaming() { return service.roaming(); }
    public FPort port() { return new FPort(); }
    public FPort port(String nameOrAddress) { return new FPort(nameOrAddress); }
    public FDownload download(String url) { return download(url, true); }
    public FDownload download(String url, boolean overwrite) { return new FDownload(url, overwrite); }
    public Map<String, Object> getConfig() { return service.getConfig(); }
    public boolean saveConfig(String name, Object value) { return service.saveConfig(name, value); }
    public Map<String, Object> getJsConfig() { return service.getJsConfig(); }
    public boolean saveJsConfig(String name, Object value) { return service.saveJsConfig(name, value); }
    public int closeAllContexts(boolean keepThisScriptAlive) {
        return service.closeAllContexts(keepThisScriptAlive);
    }

    @Library("Tag")
    public static final class Tag extends PerExecLibrary {
        private final FTag tag = new FTag();
        public Tag(BaseScriptContext<?> ctx) { super(ctx); }
        public Map<String, String> list() { return tag.list(); }
        public String add(String name, String directory) { return tag.add(name, directory); }
        public boolean remove(String name) { return tag.remove(name); }
        public String toString(String taggedPath) { return tag.toString(taggedPath); }
    }



}
