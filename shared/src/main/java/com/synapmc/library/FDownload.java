package com.synapmc.library;

import com.synapmc.SynapMcShared;

import java.io.IOException;

public class FDownload {

    public String file(String url, String savePath, boolean overwrite) {
        try {
            return SynapMcShared.downloadTo(url, savePath, overwrite);
        } catch (IOException e) {
            SynapMcShared.error("[SynapMc] download failed for " + url + ": " + e.getMessage());
            return null;
        }
    }

    public String file(String url, String savePath) {
        return file(url, savePath, true);
    }

    public String image(String url, String filepath) {
        return named(url, "@images", filepath);
    }

    public String sound(String url, String filepath) {
        return named(url, "@sounds", filepath);
    }

    public String mod(String url) {
        return file(url, "@mods", true);
    }

    private String named(String url, String tag, String filepath) {
        try {
            return SynapMcShared.downloadTo(url, tag, filepath, true);
        } catch (IOException e) {
            SynapMcShared.error("[SynapMc] download failed for " + url + ": " + e.getMessage());
            return null;
        }
    }
}
