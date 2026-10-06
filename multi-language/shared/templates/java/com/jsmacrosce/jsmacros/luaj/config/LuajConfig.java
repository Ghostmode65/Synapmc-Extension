package @CORE_PACKAGE@.luaj.config;
import @CORE_PACKAGE@.core.config.Option;

public class LuajConfig {
    @Option(translationKey = "jsmacroslua.globalcontext",
            group = {"@CORE_SETTINGS_LANGUAGES@", "jsmacroslua.settings.languages.lua"})
    public boolean useGlobalContext = false;

    @Option(translationKey = "jsmacroslua.splitglobalcontext",
            group = {"@CORE_SETTINGS_LANGUAGES@", "jsmacroslua.settings.languages.lua"})
    public boolean splitGlobalContext = false;
}
