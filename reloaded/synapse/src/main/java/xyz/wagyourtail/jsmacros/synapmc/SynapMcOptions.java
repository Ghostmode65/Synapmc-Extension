package xyz.wagyourtail.jsmacros.synapmc;

import xyz.wagyourtail.jsmacros.core.config.Option;
import com.synapmc.SynapMcSettingsUi;

public final class SynapMcOptions {
    @Option(translationKey = "synapmc.settings.autorunEnabled", group = {"synapmc.settings"}, setter = "setAutorunEnabled")
    public boolean getAutorunEnabled() { return (Boolean) SynapMcSettingsUi.get("autorunEnabled"); }
    public void setAutorunEnabled(boolean value) { SynapMcSettingsUi.set("autorunEnabled", value); }

    @Option(translationKey = "synapmc.settings.silent", group = {"synapmc.settings"}, setter = "setSilent")
    public boolean getSilent() { return (Boolean) SynapMcSettingsUi.get("silent"); }
    public void setSilent(boolean value) { SynapMcSettingsUi.set("silent", value); }

    @Option(translationKey = "synapmc.settings.tcpPortEnabled", group = {"synapmc.settings", "synapmc.settings.tcp"}, setter = "setTcpPortEnabled")
    public boolean getTcpPortEnabled() { return (Boolean) SynapMcSettingsUi.get("tcpPortEnabled"); }
    public void setTcpPortEnabled(boolean value) { SynapMcSettingsUi.set("tcpPortEnabled", value); }

    @Option(translationKey = "synapmc.settings.tcpPort", group = {"synapmc.settings", "synapmc.settings.tcp"}, setter = "setTcpPort")
    public int getTcpPort() { return (Integer) SynapMcSettingsUi.get("tcpPort"); }
    public void setTcpPort(int value) { SynapMcSettingsUi.set("tcpPort", value); }

    @Option(translationKey = "synapmc.settings.tcpPortPassword", group = {"synapmc.settings", "synapmc.settings.tcp"}, setter = "setTcpPortPassword")
    public String getTcpPortPassword() { return (String) SynapMcSettingsUi.get("tcpPortPassword"); }
    public void setTcpPortPassword(String value) { SynapMcSettingsUi.set("tcpPortPassword", value); }

    @Option(translationKey = "synapmc.settings.tcpBindAddress", group = {"synapmc.settings", "synapmc.settings.tcp"}, setter = "setTcpBindAddress")
    public String getTcpBindAddress() { return (String) SynapMcSettingsUi.get("tcpBindAddress"); }
    public void setTcpBindAddress(String value) { SynapMcSettingsUi.set("tcpBindAddress", value); }

    @Option(translationKey = "synapmc.settings.clientInfoPreAuth", group = {"synapmc.settings", "synapmc.settings.tcp"}, setter = "setClientInfoPreAuth")
    public boolean getClientInfoPreAuth() { return (Boolean) SynapMcSettingsUi.get("clientInfoPreAuth"); }
    public void setClientInfoPreAuth(boolean value) { SynapMcSettingsUi.set("clientInfoPreAuth", value); }

    @Option(translationKey = "synapmc.settings.logExecuted", group = {"synapmc.settings", "synapmc.settings.tcp"}, setter = "setLogExecuted")
    public boolean getLogExecuted() { return (Boolean) SynapMcSettingsUi.get("logExecuted"); }
    public void setLogExecuted(boolean value) { SynapMcSettingsUi.set("logExecuted", value); }

    @Option(translationKey = "synapmc.settings.downloadMaxMb", group = {"synapmc.settings"}, setter = "setDownloadMaxMb")
    public int getDownloadMaxMb() { return (Integer) SynapMcSettingsUi.get("downloadMaxMb"); }
    public void setDownloadMaxMb(int value) { SynapMcSettingsUi.set("downloadMaxMb", value); }

}
