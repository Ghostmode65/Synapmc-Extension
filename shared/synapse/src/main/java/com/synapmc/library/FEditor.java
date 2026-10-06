package com.synapmc.library;

import com.synapmc.MinecraftAccess;
import com.synapmc.SynapMcShared;

import java.io.File;

public final class FEditor {
    public boolean open(String file) { return open(file, 1); }
    public boolean open(File file) { return open(file, 1); }
    public boolean open(String file, int line) {
        if (file == null || file.isEmpty()) return false;
        return open(SynapMcShared.resolvePath(file), line);
    }
    public boolean open(File file, int line) {
        return file != null && MinecraftAccess.openEditor(file, Math.max(1, line));
    }
}
