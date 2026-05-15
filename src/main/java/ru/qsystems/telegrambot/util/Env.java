package ru.qsystems.telegrambot.util;

import java.util.Locale;

public final class Env {
    private Env() {
    }

    public static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    public static String string(String envName, String configuredValue) {
        return firstNonBlank(System.getenv(envName), configuredValue);
    }

    public static boolean bool(String envName, boolean configuredValue) {
        String raw = System.getenv(envName);
        if (raw == null || raw.isBlank()) {
            return configuredValue;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("1")
                || normalized.equals("true")
                || normalized.equals("yes")
                || normalized.equals("on");
    }

    public static int integer(String envName, int configuredValue) {
        String raw = System.getenv(envName);
        if (raw == null || raw.isBlank()) {
            return configuredValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return configuredValue;
        }
    }
}
