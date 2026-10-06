package com.synapmc.library;

import java.util.List;

public final class RepoHelper {
    public final String name;
    public RepoHelper(String name) { this.name = name; }
    public RepoScriptHelper getScripts() { return new RepoScriptHelper(name); }
    public List<String> list() { return getScripts().list(); }
    public List<String> list(boolean isInstalled) { return getScripts().list(isInstalled); }
}
