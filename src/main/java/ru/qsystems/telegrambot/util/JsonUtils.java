package ru.qsystems.telegrambot.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonUtils {
    private JsonUtils() {
    }

    public static Map<String, String> parseStringMap(ObjectMapper objectMapper, String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(raw, new TypeReference<>() {});
            Map<String, String> result = new LinkedHashMap<>();
            parsed.forEach((key, value) -> {
                if (value != null) {
                    result.put(String.valueOf(key), String.valueOf(value));
                }
            });
            return result;
        } catch (IOException e) {
            throw new IllegalArgumentException("JSON object expected: " + raw, e);
        }
    }

    public static Map<String, Boolean> parseBooleanMap(ObjectMapper objectMapper, String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(raw, new TypeReference<>() {});
            Map<String, Boolean> result = new LinkedHashMap<>();
            parsed.forEach((key, value) -> {
                if (value != null) {
                    result.put(String.valueOf(key), toBoolean(value));
                }
            });
            return result;
        } catch (IOException e) {
            throw new IllegalArgumentException("JSON object expected: " + raw, e);
        }
    }

    public static List<Map<String, Object>> parseListOfObjects(ObjectMapper objectMapper, String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (IOException e) {
            throw new IllegalArgumentException("JSON array expected: " + raw, e);
        }
    }

    public static boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value).trim().toLowerCase();
        return text.equals("1") || text.equals("true") || text.equals("yes") || text.equals("on");
    }
}
