package ru.qsystems.telegrambot.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import ru.qsystems.telegrambot.util.Env;

@ConfigurationProperties("bot.telegram")
public class TelegramProperties {
    private String token = "";
    private String apiUrl = "https://api.telegram.org";
    private boolean pollingEnabled = true;
    private int pollingTimeoutSeconds = 30;

    public String getToken() {
        return Env.string("API_TOKEN", token);
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getApiUrl() {
        return Env.string("TELEGRAM_API_URL", apiUrl);
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public boolean isPollingEnabled() {
        return Env.bool("TELEGRAM_POLLING_ENABLED", pollingEnabled);
    }

    public void setPollingEnabled(boolean pollingEnabled) {
        this.pollingEnabled = pollingEnabled;
    }

    public int getPollingTimeoutSeconds() {
        return Env.integer("TELEGRAM_POLLING_TIMEOUT_SECONDS", pollingTimeoutSeconds);
    }

    public void setPollingTimeoutSeconds(int pollingTimeoutSeconds) {
        this.pollingTimeoutSeconds = pollingTimeoutSeconds;
    }

    public boolean hasToken() {
        String value = getToken();
        return value != null && !value.isBlank();
    }
}
