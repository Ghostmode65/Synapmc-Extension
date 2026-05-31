package com.synapmc.library;

import com.synapmc.SynapMcShared;

import java.util.LinkedHashMap;
import java.util.Map;

public class FTag {

    public Map<String, String> list() {
        Map<String, String> tags = new LinkedHashMap<String, String>();
        if (SynapMcShared.globalConfig != null && SynapMcShared.globalConfig.pathTags != null) {
            tags.putAll(SynapMcShared.globalConfig.pathTags);
        }
        if (SynapMcShared.core != null && SynapMcShared.core.getPathTags() != null) {
            tags.putAll(SynapMcShared.core.getPathTags());
        }
        return tags;
    }

    public String add(String tagName, String path) {
        String key = normalize(tagName);
        if (key.isEmpty() || path == null || path.isEmpty()) return null;
        if (SynapMcShared.globalConfig == null) SynapMcShared.initGlobalConfig();
        SynapMcShared.globalConfig.pathTags.put(key, path);
        if (SynapMcShared.core != null && SynapMcShared.core.getPathTags() != null) {
            SynapMcShared.core.getPathTags().put(key, path);
            SynapMcShared.core.saveConfig();
        }
        SynapMcShared.globalConfig.save(SynapMcShared.roamingDir);
        return key;
    }

    public boolean remove(String tagName) {
        String key = normalize(tagName);
        if (key.isEmpty()) return false;
        boolean removed = false;
        if (SynapMcShared.globalConfig != null && SynapMcShared.globalConfig.pathTags != null) {
            removed = SynapMcShared.globalConfig.pathTags.remove(key) != null;
            SynapMcShared.globalConfig.save(SynapMcShared.roamingDir);
        }
        if (SynapMcShared.core != null && SynapMcShared.core.getPathTags() != null) {
            removed = SynapMcShared.core.getPathTags().remove(key) != null || removed;
            SynapMcShared.core.saveConfig();
        }
        return removed;
    }

    public String toString(String taggedPath) {
        return SynapMcShared.resolvePath(taggedPath).getAbsolutePath();
    }

    private String normalize(String tagName) {
        if (tagName == null) return "";
        String key = tagName.trim();
        while (key.startsWith("@")) key = key.substring(1);
        return key;
    }
}
