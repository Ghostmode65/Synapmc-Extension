package com.synapmc.library;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.synapmc.SynapMcShared;

/**
 * Provides scripting access to the TCP bridge server (formerly called "webhook").
 * Connect external programs to the bridge server to receive broadcasts and execute scripts.
 */
public class FPort {

    private static final Gson GSON = new Gson();
    private final String target;

    public FPort() {
        this(null);
    }

    public FPort(String target) {
        this.target = target;
    }

    public void send(String type, String message) {
        if (SynapMcShared.server == null || !SynapMcShared.server.hasClients()) return;
        JsonObject obj = new JsonObject();
        obj.addProperty("type", type);
        obj.addProperty("message", message);
        sendJson(GSON.toJson(obj));
    }

    public void log(String message) {
        if (SynapMcShared.server == null || !SynapMcShared.server.hasClients()) return;
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "log");
        obj.addProperty("message", message);
        sendJson(GSON.toJson(obj));
    }

    public void sendRaw(String json) {
        if (SynapMcShared.server == null || !SynapMcShared.server.hasClients()) return;
        sendJson(json);
    }

    public void clientInfo() {
        if (SynapMcShared.server == null || !SynapMcShared.server.hasClients()) return;
        if (SynapMcShared.core == null) return;
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "client_info");
        obj.add("client", GSON.toJsonTree(SynapMcShared.core.getClientInfo()));
        sendJson(GSON.toJson(obj));
    }

    public boolean screenshot(String name) {
        return FScreenshot.send(target, name, null, null, null, null, null);
    }

    public boolean screenshot(String name, String resolution) {
        return FScreenshot.send(target, name, resolution, null, null, null, null);
    }

    public boolean screenshot(String name, String resolution, int x, int y, int width, int height) {
        return FScreenshot.send(target, name, resolution, x, y, width, height);
    }

    public boolean isConnected() {
        if (SynapMcShared.server == null) return false;
        return target == null || target.trim().isEmpty()
                ? SynapMcShared.server.hasClients()
                : SynapMcShared.server.hasClient(target);
    }

    private void sendJson(String json) {
        if (target == null || target.trim().isEmpty()) {
            SynapMcShared.server.broadcast(json);
        } else {
            SynapMcShared.server.sendTo(target, json);
        }
    }
}
