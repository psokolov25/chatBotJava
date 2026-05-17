package ru.qsystems.telegrambot.api;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.serde.annotation.Serdeable;
import ru.qsystems.telegrambot.telegram.UserStateStore;

import java.util.Map;

@Controller("/api/bot/admin")
public class UserSessionAdminController {
    private final UserStateStore userStateStore;

    public UserSessionAdminController(UserStateStore userStateStore) {
        this.userStateStore = userStateStore;
    }

    @Post("/reset-session")
    public Map<String, Object> resetSession(@Body ResetSessionRequest request) {
        if (request == null || request.userId() == null || request.userId() <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        userStateStore.reset(request.userId());
        return Map.of("status", "ok", "userId", request.userId());
    }

    @Serdeable
    public record ResetSessionRequest(Long userId) {
    }
}
