package ru.qsystems.telegrambot.model;

public record BranchConnection(
        QueueSystem queueSystem,
        String baseUrl,
        String login,
        String password
) {
    public String normalizedBaseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
