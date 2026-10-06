package com.synapmc.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.synapmc.SynapMcShared;
import com.synapmc.MinecraftAccess;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;

public class BridgeServer {

    private static final Gson GSON = new Gson();
    private static final int MAX_MESSAGE_CHARS = 128 * 1024;

    private final int port;
    private final String password;
    private final String bindAddress;
    private ServerSocket serverSocket;
    private final ConcurrentHashMap<PrintWriter, ClientInfo> clientInfo = new ConcurrentHashMap<PrintWriter, ClientInfo>();

    public BridgeServer(int port, String password, String bindAddress) {
        this.port = port;
        this.password = password != null ? password : "";
        this.bindAddress = bindAddress != null ? bindAddress.trim() : "";
    }

    private boolean requiresAuth() {
        return !password.isEmpty();
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket();
        try {
            serverSocket.bind(bindAddress.isEmpty()
                    ? new InetSocketAddress(port)
                    : new InetSocketAddress(InetAddress.getByName(bindAddress), port));
        } catch (IOException failure) {
            serverSocket.close();
            throw failure;
        }
        Thread t = new Thread(this::acceptLoop, "SynapMc-TCPPort-Accept");
        t.setDaemon(true);
        t.start();
        SynapMcShared.info("[SynapMc] TCP port listening on "
                + (bindAddress.isEmpty() ? "all interfaces" : bindAddress) + ":" + port);
    }

    public void stop() {
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
        for (ClientInfo client : clientInfo.values()) {
            try { client.socket.close(); } catch (IOException ignored) {}
        }
        clientInfo.clear();
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Socket client = serverSocket.accept();
                handleClient(client);
            } catch (IOException e) {
                if (!serverSocket.isClosed()) SynapMcShared.error("[SynapMc] TCP accept failed: " + e.getMessage());
            }
        }
    }

    private void handleClient(Socket socket) {
        PrintWriter writer;
        try {
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8), true);
        } catch (IOException e) {
            SynapMcShared.error("[SynapMc] TCP client failed: " + e.getMessage());
            return;
        }

        Thread t = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                ClientInfo client = new ClientInfo(writer, socket);
                sendClientInfo(writer);
                if (requiresAuth()) {
                    String line = readMessage(reader);
                    if (line == null) return;
                    try {
                        JsonObject msg = new JsonParser().parse(line.trim()).getAsJsonObject();
                        String type = msg.has("type") ? msg.get("type").getAsString() : "";
                        String pass = msg.has("password") ? msg.get("password").getAsString() : "";
                        if (!"auth".equals(type) || !password.equals(pass)) {
                            writer.println(errorJson("Authentication failed", null, -1));
                            return;
                        }
                        updateClientName(client, msg);
                    } catch (Exception e) {
                        writer.println(errorJson("Authentication failed: invalid message", null, -1));
                        return;
                    }
                    writer.println("{\"type\":\"auth\",\"status\":\"ok\"}");
                }

                clientInfo.put(writer, client);
                if (!requiresAuth()) {
                    writer.println("{\"type\":\"connected\",\"status\":\"ok\"}");
                }
                SynapMcShared.info("[SynapMc] Bridge client connected. (" + clientInfo.size() + " connected)");
                String line;
                while ((line = readMessage(reader)) != null) {
                    String msg = line.trim();
                    if (!msg.isEmpty()) handleMessage(client, msg);
                }
            } catch (MessageTooLongException tooLong) {
                writer.println(errorJson("TCP message exceeds 128 Ki characters", null, -1));
            } catch (IOException ignored) {
            } finally {
                clientInfo.remove(writer);
                SynapMcShared.info("[SynapMc] Bridge client disconnected. (" + clientInfo.size() + " connected)");
                try { socket.close(); } catch (IOException ignored) {}
            }
        }, "SynapMc-TCPPort-Client");
        t.setDaemon(true);
        t.start();
    }

    private void handleMessage(ClientInfo client, String json) {
        try {
            JsonObject msg = new JsonParser().parse(json).getAsJsonObject();
            String type = msg.has("type") ? msg.get("type").getAsString() : "";

            switch (type) {
                case "identify":
                case "client_name": {
                    updateClientName(client, msg);
                    client.writer.println("{\"type\":\"identified\",\"name\":\"" + escapeJson(client.name) + "\"}");
                    break;
                }
                case "exec": {
                    String lang = msg.has("lang") ? msg.get("lang").getAsString() : "js";
                    String script = msg.has("script") ? msg.get("script").getAsString() : "";
                    File fakeFile = new File(new File("").getAbsoluteFile(), "synapmc-tcpport." + lang);
                    if (SynapMcShared.config.logExecuted) {
                        MinecraftAccess.notifyExecutedScript(client.name, lang, script);
                    }
                    SynapMcShared.core.exec(lang, script, fakeFile, ex -> {
                        String err = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                        client.writer.println(errorJson(err, null, -1));
                    });
                    break;
                }
                default: {
                    client.writer.println(errorJson("Unknown message type: " + type, null, -1));
                    break;
                }
            }
        } catch (Exception e) {
            client.writer.println(errorJson("Failed to parse message: " + e.getMessage(), null, -1));
        }
    }

    public void broadcast(String json) {
        broadcastChecked(json);
    }

    private static String readMessage(BufferedReader reader) throws IOException {
        StringBuilder message = new StringBuilder();
        int next;
        while ((next = reader.read()) != -1) {
            if (next == '\n') return message.toString();
            if (message.length() >= MAX_MESSAGE_CHARS) throw new MessageTooLongException();
            message.append((char) next);
        }
        return message.length() == 0 ? null : message.toString();
    }

    private static final class MessageTooLongException extends IOException { }

    public boolean broadcastChecked(String json) {
        boolean sent = false;
        for (PrintWriter writer : clientInfo.keySet()) {
            writer.println(json);
            if (!writer.checkError()) sent = true;
        }
        return sent;
    }

    public boolean sendTo(String target, String json) {
        ClientInfo client = findClient(target);
        if (client == null) return false;
        client.writer.println(json);
        return !client.writer.checkError();
    }

    public boolean hasClients() {
        return !clientInfo.isEmpty();
    }

    public boolean hasClient(String target) {
        return findClient(target) != null;
    }

    private void sendClientInfo(PrintWriter writer) {
        if (SynapMcShared.core == null || !SynapMcShared.core.isClientInfoPreAuth()) return;
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "client_info");
        obj.add("client", GSON.toJsonTree(SynapMcShared.core.getClientInfo()));
        writer.println(GSON.toJson(obj));
    }

    public static String errorJson(String message, String file, int line) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "error");
        obj.addProperty("message", message != null ? message : "");
        if (file != null) obj.addProperty("file", file);
        if (line > -1) obj.addProperty("line", line);
        return GSON.toJson(obj);
    }

    private void updateClientName(ClientInfo client, JsonObject msg) {
        if (msg == null || !msg.has("name") || msg.get("name").isJsonNull()) return;
        String name = msg.get("name").getAsString();
        if (name != null && !name.trim().isEmpty()) client.name = name.trim();
    }

    private ClientInfo findClient(String target) {
        if (target == null || target.trim().isEmpty()) return null;
        String needle = target.trim();
        for (ClientInfo client : clientInfo.values()) {
            if (client.matches(needle)) return client;
        }
        return null;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static class ClientInfo {
        final PrintWriter writer;
        final Socket socket;
        final String ip;
        final String host;
        final int port;
        volatile String name;

        ClientInfo(PrintWriter writer, Socket socket) {
            this.writer = writer;
            this.socket = socket;
            this.ip = socket.getInetAddress() != null ? socket.getInetAddress().getHostAddress() : "";
            this.host = socket.getInetAddress() != null ? socket.getInetAddress().getHostName() : "";
            this.port = socket.getPort();
        }

        boolean matches(String target) {
            return (name != null && name.equalsIgnoreCase(target))
                    || (name != null && (name + ":" + port).equalsIgnoreCase(target))
                    || ip.equals(target)
                    || host.equalsIgnoreCase(target)
                    || (ip + ":" + port).equals(target)
                    || (host + ":" + port).equalsIgnoreCase(target);
        }
    }
}
