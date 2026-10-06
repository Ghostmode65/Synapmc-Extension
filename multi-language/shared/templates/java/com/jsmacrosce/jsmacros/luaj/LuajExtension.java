package @CORE_PACKAGE@.luaj;

import com.google.common.collect.Sets;
import @CORE_PACKAGE@.core.Core;
import @CORE_PACKAGE@.core.extensions.Extension;
import @CORE_PACKAGE@.core.extensions.LanguageExtension;
import @CORE_PACKAGE@.core.extensions.LibraryExtension;
import @CORE_PACKAGE@.core.language.BaseLanguage;
import @CORE_PACKAGE@.core.language.BaseWrappedException;
import @CORE_PACKAGE@.core.library.BaseLibrary;
import @CORE_PACKAGE@.luaj.config.LuajConfig;
import @CORE_PACKAGE@.luaj.language.impl.LuajLanguageDefinition;
import @CORE_PACKAGE@.luaj.library.impl.FWrapper;
import @CORE_PACKAGE@.luaj.library.impl.LuajGlobalContextLib;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.jsmacros.multilang.shared.ClassLoaderContext;
import org.jsmacros.multilang.shared.LanguageFileMatch;

import java.io.File;
import java.net.URL;
import java.util.Map;
import java.util.Set;

public class LuajExtension implements LanguageExtension, LibraryExtension {

    private static LuajLanguageDefinition langDef;
    public static Core<?, ?> RUNNER;

    @Override
    public String getExtensionName() { return "@LUA_EXTENSION_NAME@"; }

    @Override
    public void init(Core<?, ?> runner) {
        RUNNER = runner;
        try {
            runner.config.addOptions("lua", LuajConfig.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Thread t = new Thread(() -> {
            try { JsePlatform.standardGlobals().load("").call(); } catch (Exception ignored) {}
        });
        t.setDaemon(true);
        t.start();
    }

    @Override
    public Set<URL> getDependencies() {
        return Extension.getDependenciesInternal(LuajExtension.class, "@LUA_DESCRIPTOR@");
    }

    @Override
    public Map<String, String> getTranslations(String lang) {
        return Extension.getTranslationsInternal(LuajExtension.class, "@LUA_TRANSLATIONS@" + lang + ".json");
    }

    @Override
    public int getPriority() { return 500; }

    @Override
    public ExtMatch extensionMatch(File fname) {
        return switch (LanguageFileMatch.match(fname, "lua", "@LUA_PREFERRED_NAME@", true)) {
            case NAMED -> ExtMatch.MATCH_WITH_NAME;
            case MATCH -> ExtMatch.MATCH;
            case NONE -> ExtMatch.NOT_MATCH;
        };
    }

    @Override
    public String defaultFileExtension() { return "lua"; }

    @Override
    public BaseLanguage<?, ?> getLanguage(Core<?, ?> runner) {
        if (langDef == null) {
            langDef = ClassLoaderContext.call(LuajLanguageDefinition.class.getClassLoader(),
                () -> new LuajLanguageDefinition(this, runner));
        }
        return langDef;
    }

    @Override
    public Set<Class<? extends BaseLibrary>> getLibraries() {
        return Sets.newHashSet(FWrapper.class, LuajGlobalContextLib.class);
    }

    @Override
    public BaseWrappedException<?> wrapException(Throwable ex) {
        if (!(ex instanceof LuaError)) return null;
        LuaError err = (LuaError) ex;

        File file = null;
        int line  = -1;
        try {
            file = err.getFile();
            line = err.getLine();
        } catch (NoSuchMethodError ignored) {}

        String msg;
        try {
            msg = err.getErrorMessage();
        } catch (NoSuchMethodError ignored) {
            msg = err.getMessage();
        }
        if (msg != null) {
            int nl = msg.indexOf('\n');
            if (nl > 0) msg = msg.substring(0, nl);
        }
        BaseWrappedException.SourceLocation loc = file != null
            ? new BaseWrappedException.GuestLocation(file, -1, -1, line, -1) : null;
        BaseWrappedException<?> cause = ex.getCause() != null
            ? RUNNER.wrapException(ex.getCause()) : null;
        return new BaseWrappedException<>(ex, msg, loc, cause);
    }

    @Override
    public boolean isGuestObject(Object o) { return o instanceof LuaValue; }
}
