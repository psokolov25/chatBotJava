package ru.qsystems.telegrambot.path;

import java.util.Locale;

public enum MultiServicesAction {
    AUTO,
    CHOOSE,
    CHOOSE_MANY;

    public static MultiServicesAction from(String raw) {
        if (raw == null || raw.isBlank()) {
            return CHOOSE;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "auto" -> AUTO;
            case "choose_many", "choose-many", "chooseMany" -> CHOOSE_MANY;
            default -> CHOOSE;
        };
    }
}
