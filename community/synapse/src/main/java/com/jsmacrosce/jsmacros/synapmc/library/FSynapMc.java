package com.jsmacrosce.jsmacros.synapmc.library;

import com.jsmacrosce.jsmacros.core.library.Library;
import com.jsmacrosce.jsmacros.core.library.PerExecLibrary;
import com.jsmacrosce.jsmacros.core.language.BaseScriptContext;
import com.synapmc.library.FClient;
import com.synapmc.library.FDownload;
import com.synapmc.library.FEditor;
import com.synapmc.library.FExtra;
import com.synapmc.library.FPort;
import com.synapmc.library.FSynapse;
import com.synapmc.library.FTag;
import com.synapmc.library.FTexture;
import com.synapmc.ScoreboardSpoof;

import java.io.File;
import java.util.Map;
import java.util.List;
import com.synapmc.library.RepoHelper;
import com.synapmc.library.RepoScriptHelper;

@Library("synapmc")
public class FSynapMc extends PerExecLibrary {
    private final FSynapse service;

    public FSynapMc(BaseScriptContext<?> ctx) {
        super(ctx);
        service = new FSynapse(ctx.runner, ctx);
    }

    @Library("synapse")
    public static final class Synapse extends FSynapMc {
        public Synapse(BaseScriptContext<?> ctx) { super(ctx); }
    }

    public RepoHelper getRepo(String name) { return service.getRepo(name); }
    public RepoScriptHelper getScripts() { return service.getScripts(); }
    public List<String> list() { return service.list(); }
    public List<String> list(boolean installed) { return service.list(installed); }
    public void addRepo(List<String> urls, boolean update) { service.addRepo(urls, update); }
    public void addRepo(List<String> urls) { service.addRepo(urls); }
    public void addRepo(String url, boolean update) { service.addRepo(url, update); }
    public void addRepo(String url) { service.addRepo(url); }
    public void removeRepo(List<String> urls) { service.removeRepo(urls); }
    public void removeRepo(String nameOrUrl) { service.removeRepo(nameOrUrl); }

    public String roaming() { return service.roaming(); }
    public FPort port() { return new FPort(); }
    public FPort port(String nameOrAddress) { return new FPort(nameOrAddress); }
    public String download(String url) { return download(url, null, true); }
    public String download(String url, boolean overwrite) { return download(url, null, overwrite); }
    public String download(String url, String filepath) { return download(url, filepath, true); }
    public String download(String url, String filepath, boolean overwrite) {
        return new FDownload(url, overwrite).file(filepath);
    }
    public Map<String, Object> getConfig() { return service.getConfig(); }
    public boolean saveConfig(String name, Object value) { return service.saveConfig(name, value); }
    public Map<String, Object> getJsConfig() { return service.getJsConfig(); }
    public boolean saveJsConfig(String name, Object value) { return service.saveJsConfig(name, value); }
    public int closeAllContexts(boolean keepThisScriptAlive) {
        return service.closeAllContexts(keepThisScriptAlive);
    }

    @Library("SClient")
    public static final class Client extends PerExecLibrary {
        private final FClient client = new FClient();
        public Client(BaseScriptContext<?> ctx) { super(ctx); }
        public String getOS() { return client.getOS(); }
        public String jsVersion() { return client.jsVersion(); }
        public boolean isFullscreen() { return client.isFullscreen(); }
        public boolean toggleFullscreen(boolean enabled) { return client.toggleFullscreen(enabled); }
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

    @Library("SEditor")
    public static final class Editor extends PerExecLibrary {
        private final FEditor editor = new FEditor();
        public Editor(BaseScriptContext<?> ctx) { super(ctx); }
        public boolean open(String file) { return editor.open(file); }
        public boolean open(String file, int line) { return editor.open(file, line); }
        public boolean open(File file) { return editor.open(file); }
        public boolean open(File file, int line) { return editor.open(file, line); }
    }

    @Library("SExtra")
    public static final class Extra extends PerExecLibrary {
        private final FExtra extra = new FExtra();
        public final FTexture texture = extra.texture;
        public Extra(BaseScriptContext<?> ctx) { super(ctx); }
        public ScoreboardSpoof.Handle scoreboard(int line, String text) {
            return extra.scoreboard(line, text);
        }
    }
}
