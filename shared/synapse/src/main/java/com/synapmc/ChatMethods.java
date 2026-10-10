package com.synapmc;

/** Synapmc's outgoing scripting-chat setting. Local Chat.log is independent. */
public final class ChatMethods {
    private ChatMethods() {}
    public static boolean canSay(String message) {
        SynapMcConfig config = SynapMcShared.config;
        return config == null || config.chatSayEnabled || message != null && message.startsWith("/");
    }
}
