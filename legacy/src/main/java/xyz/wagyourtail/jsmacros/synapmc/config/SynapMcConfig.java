package xyz.wagyourtail.jsmacros.synapmc.config;

import xyz.wagyourtail.jsmacros.core.config.Option;
import xyz.wagyourtail.jsmacros.core.config.OptionType;
import com.synapmc.repo.RepoManager;

import java.util.LinkedHashMap;
import java.util.Map;

public class SynapMcConfig {

    @Option(translationKey = "AutorunEnabled", group = {"synapmc"})
    public boolean loadEnabled = true;

    @Option(translationKey = "LoadDirectory", group = {"synapmc"})
    public String loadDirectory = "";

    @Option(translationKey = "Enabled", group = {"synapmc", "Tcp Port"})
    public boolean tcpPortEnabled = false;

    @Option(translationKey = "Port", group = {"synapmc", "Tcp Port"})
    public int tcpPort = 8765;

    @Option(translationKey = "Password", group = {"synapmc", "Tcp Port"})
    public String tcpPortPassword = "";

    @Option(translationKey = "ClientInfoPreAuth", group = {"synapmc", "Tcp Port"})
    public boolean clientInfoPreAuth = true;

    @Option(translationKey = "OpenRepoBrowser", group = {"synapmc", "Repos"}, setter = "setOpenDashboard")
    public boolean getOpenDashboard() {
        return false;
    }

    public void setOpenDashboard(boolean ignored) {
        RepoManager.getInstance().getDashboard().openDashboard();
    }

    @Option(translationKey = "AutoRegisterTriggers", group = {"synapmc", "Repos"})
    public boolean autoRegisterTriggers = false;

    @Option(translationKey = "RepoSources", group = {"synapmc", "Repos", "Sources"}, type = @OptionType(value = "string"))
    public Map<String, String> repos = new LinkedHashMap<String, String>() {{
        put("luaJ", "https://raw.githubusercontent.com/Ghostmode65/luaJ/main/manifest.json");
        put("SynapMc", "https://raw.githubusercontent.com/Ghostmode65/Synapcmc-Extension/refs/heads/main/repository/manifest.json");
    }};

    /**
     * Folders to link into the jsMacros macros directory as "unified" junctions.
     * Key = junction name, Value = roaming-relative folder path.
     */
    @Option(translationKey = "UnifyFolders", group = {"synapmc", "Unify"}, type = @OptionType(value = "string"))
    public Map<String, String> unifyFolders = new LinkedHashMap<String, String>() {{
        put("macros", "scripts/macros");
    }};

    @Option(translationKey = "PathTags", group = {"synapmc", "Paths"}, type = @OptionType(value = "string"))
    public Map<String, String> pathTags = new LinkedHashMap<String, String>() {{
        put("library", "scripts/library");
        put("macros", "scripts/macros");
        put("projects", "projects");
        put("images", "assets/images");
        put("sounds", "assets/sounds");
    }};

    @Option(translationKey = "Debug", group = {"synapmc", "Dev"})
    public boolean debug = false;

    @Option(translationKey = "Silent", group = {"synapmc", "Dev"})
    public boolean silent = false;
}
