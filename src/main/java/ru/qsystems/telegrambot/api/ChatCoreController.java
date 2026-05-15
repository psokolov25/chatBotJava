package ru.qsystems.telegrambot.api;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Чат API", description = "REST API для прохождения сценария получения талона внешним фронтендом")
@Controller("/api/chat")
public class ChatCoreController {
    private final ChatCoreService service;

    public ChatCoreController(ChatCoreService service) { this.service = service; }

    @Operation(summary = "Инициализация чата", description = "Создает новую сессию посетителя и возвращает первый шаг сценария")
    @ApiResponse(responseCode = "200", description = "Сессия создана", content = @Content(schema = @Schema(implementation = ChatCoreService.CoreResponse.class)))
    @Post("/init")
    public ChatCoreService.CoreResponse init(@Body @Nullable ChatCoreService.InitRequest request) { return service.initialize(request); }

    @Operation(summary = "Выполнение действия", description = "Передает действие по текущему шагу сценария и получает следующий шаг")
    @ApiResponse(responseCode = "200", description = "Следующий шаг сценария", content = @Content(schema = @Schema(implementation = ChatCoreService.CoreResponse.class)))
    @Post("/{sessionId}/action")
    public ChatCoreService.CoreResponse action(String sessionId, @Body ActionRequest request) {
        String type = request.type();
        String value = request.value();
        if (type == null && request.action() != null) {
            String[] parts = request.action().split(":", 2);
            type = parts[0];
            value = parts.length > 1 ? parts[1] : "";
        }
        return service.act(sessionId, new ChatCoreService.CoreAction(type, value, request.customerName()));
    }

    @Schema(name = "ChatActionRequest", description = "Запрос действия посетителя в рамках сессии")
    public record ActionRequest(
            @Schema(description = "Legacy формат действия, например select-service:123", example = "select-service:123") String action,
            @Schema(description = "Тип действия", example = "select-service") String type,
            @Schema(description = "Значение действия", example = "123") String value,
            @Schema(description = "Имя/ФИО посетителя для передачи в параметры визита", example = "Иван Иванов") String customerName
    ) {}
}
