package ru.qsystems.telegrambot;

import org.junit.jupiter.api.Test;
import ru.qsystems.telegrambot.util.PiiSanitizer;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PiiSanitizerTest {
    @Test
    void masksPersonalFieldsRecursively() {
        PiiSanitizer sanitizer = new PiiSanitizer();
        Map<String, Object> result = sanitizer.sanitize(Map.of(
                "TelegramCustomerId", "1",
                "ticketId", "A001",
                "nested", Map.of("email", "user@example.com")
        ));

        assertEquals("***", result.get("TelegramCustomerId"));
        assertEquals("A001", result.get("ticketId"));
        assertEquals("***", ((Map<?, ?>) result.get("nested")).get("email"));
    }
}
