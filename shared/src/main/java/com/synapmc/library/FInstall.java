package com.synapmc.library;

import com.synapmc.repo.RepoManager;

/**
 * Returned by {@code synapmc:repo():install()}.
 * Each method handles a distinct category of install.
 *
 * Usage:
 *   synapmc:repo():install():script("GradientText")
 *   synapmc:repo():install():script("GradientText", "load")
 *   synapmc:repo():install():jar("SynapMcLua")
 *   synapmc:repo():install():exe("SomeTool")
 */
public class FInstall {

    private final String repoScope;

    public FInstall(String repoScope) {
        this.repoScope = repoScope;
    }

    /**
     * Installs a macro or api script. Downloads as UTF-8 text and places it
     * in the destination declared in the manifest, or {@code location} as fallback.
     * Resolves and installs dependencies first. Skips if already up-to-date.
     *
     * @param name     script name as declared in the manifest
     * @param location fallback destination: "library", "load", or a custom relative path
     * @return absolute path of the installed file, or null on failure
     */
    public String script(String name, String location) {
        if (repoScope != null) {
            return RepoManager.getInstance().installScript(name, location, repoScope);
        }
        return RepoManager.getInstance().installScript(name, location);
    }

    /** Installs a script with the default location ("library"). */
    public String script(String name) {
        return script(name, "library");
    }

    /**
     * Installs a JAR language extension. Downloads as raw bytes and places it
     * alongside the running SynapMc JAR so JsMacros picks it up on next launch.
     * Logs a restart warning — the game must be restarted before the JAR takes effect.
     */
    public String jar(String name) {
        return RepoManager.getInstance().installJar(name, repoScope);
    }

    /**
     * Downloads an executable to the user's Desktop. Not tracked in the installed
     * index — this is purely a file download, not a script install.
     */
    public String exe(String name) {
        return RepoManager.getInstance().installExe(name, repoScope);
    }
}
