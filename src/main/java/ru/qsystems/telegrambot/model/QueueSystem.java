package ru.qsystems.telegrambot.model;

import java.util.Locale;

public enum QueueSystem {
    ORCHESTRA,
    AXIOMA;

    public static QueueSystem from(String value, QueueSystem fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "orchestra" -> ORCHESTRA;
            case "axioma" -> AXIOMA;
            default -> fallback;
        };
    }
}
