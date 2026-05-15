package ru.qsystems.telegrambot.model;

import java.util.Map;
import java.util.Objects;

public record ServiceInfo(String id, String name, Map<String, Object> raw) {
    public static ServiceInfo from(Map<String, Object> raw) {
        Object id = raw.get("id");
        String name = firstNonBlank(raw.get("internalName"), raw.get("name"), id);
        return new ServiceInfo(Objects.toString(id, ""), name, raw);
    }

    private static String firstNonBlank(Object... values) {
        for (Object value : values) {
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return "";
    }
}
