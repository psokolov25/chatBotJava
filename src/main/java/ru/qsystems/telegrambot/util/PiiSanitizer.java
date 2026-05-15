package ru.qsystems.telegrambot.util;

import jakarta.inject.Singleton;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Singleton
public class PiiSanitizer {
    private static final Set<String> PII_KEYS = Set.of(
            "telegramcustomerid",
            "telegramchatid",
            "telegramcustomerfullname",
            "customerid",
            "customername",
            "phone",
            "email",
            "passport"
    );

    @SuppressWarnings("unchecked")
    public Map<String, Object> sanitize(Map<String, ?> payload) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        if (payload == null) {
            return sanitized;
        }
        payload.forEach((key, value) -> {
            if (key != null && value != null && PII_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
                sanitized.put(key, "***");
            } else if (value instanceof Map<?, ?> nested) {
                sanitized.put(key, sanitize((Map<String, ?>) nested));
            } else {
                sanitized.put(key, value);
            }
        });
        return sanitized;
    }
}
