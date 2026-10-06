package @CORE_PACKAGE@.graal.python;

import org.graalvm.polyglot.Context;
import @CORE_PACKAGE@.core.Core;
import @CORE_PACKAGE@.core.extensions.Extension;

public class PythonExtension implements Extension {

    @Override
    public String getExtensionName() {
        return "graalpy";
    }

    @Override
    public void init(Core<?, ?> runner) {
        Thread t = new Thread(() -> {
            Context.Builder build = Context.newBuilder("python");
            Context con = build.build();
            con.eval("python", "print('py pre-loaded.')");
            con.close();
        });
        t.start();
    }

}
