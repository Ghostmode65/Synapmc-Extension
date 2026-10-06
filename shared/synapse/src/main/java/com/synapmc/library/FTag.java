package com.synapmc.library;

import com.synapmc.SynapMcShared;

import java.util.LinkedHashMap;
import java.util.Map;

public final class FTag {
    public Map<String, String> list() {
        Map<String, String> values = new LinkedHashMap<String, String>();
        if (SynapMcShared.config != null) values.putAll(SynapMcShared.config.tags);
        values.putAll(SynapMcShared.builtInTags());
        return values;
    }

    public String add(String name, String directory) {
        String key = normalize(name);
        if (key.isEmpty() || directory == null || directory.trim().isEmpty()
                || SynapMcShared.builtInTags().containsKey(key)) return null;
        SynapMcShared.config.tags.put(key, directory);
        SynapMcShared.saveConfig();
        return key;
    }

    public boolean remove(String name) {
        String key = normalize(name);
        if (SynapMcShared.builtInTags().containsKey(key)) return false;
        boolean removed = SynapMcShared.config.tags.remove(key) != null;
        if (removed) SynapMcShared.saveConfig();
        return removed;
    }

    public String toString(String taggedPath) {
        return SynapMcShared.resolvePath(taggedPath).getAbsolutePath();
    }

    private static String normalize(String name) {
        if (name == null) return "";
        String key = name.trim();
        while (key.startsWith("@")) key = key.substring(1);
        return key.contains("/") || key.contains("\\") ? "" : key;
    }
}
