package ru.qsystems.telegrambot.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import ru.qsystems.telegrambot.util.Env;

@ConfigurationProperties("bot.cometd")
public class CometdProperties {
    private boolean enabled = true;
    private String initChannel = "/events/INIT";
    private int connectTimeoutSeconds = 90;
    private int watchdogConnectTimeoutSeconds = 120;

    public boolean isEnabled() { return Env.bool("ORCHESTRA_COMETD_ENABLED", enabled); }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getInitChannel() { return Env.string("ORCHESTRA_COMETD_INIT_CHANNEL", initChannel); }
    public void setInitChannel(String initChannel) { this.initChannel = initChannel; }
    public int getConnectTimeoutSeconds() { return Env.integer("ORCHESTRA_COMETD_CONNECT_TIMEOUT_SECONDS", connectTimeoutSeconds); }
    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) { this.connectTimeoutSeconds = connectTimeoutSeconds; }
    public int getWatchdogConnectTimeoutSeconds() { return Env.integer("ORCHESTRA_COMETD_WATCHDOG_TIMEOUT_SECONDS", watchdogConnectTimeoutSeconds); }
    public void setWatchdogConnectTimeoutSeconds(int watchdogConnectTimeoutSeconds) { this.watchdogConnectTimeoutSeconds = watchdogConnectTimeoutSeconds; }
}
