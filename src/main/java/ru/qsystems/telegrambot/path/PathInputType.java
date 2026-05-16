package ru.qsystems.telegrambot.path;

public enum PathInputType {
    OPTION,
    TEXT;

    public static PathInputType from(String value) {
        if (value == null || value.isBlank()) return OPTION;
        return "text".equalsIgnoreCase(value) ? TEXT : OPTION;
    }
}
