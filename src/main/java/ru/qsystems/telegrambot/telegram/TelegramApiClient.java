package ru.qsystems.telegrambot.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.qsystems.telegrambot.config.TelegramProperties;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Singleton
public class TelegramApiClient {
    private static final Logger LOG = LoggerFactory.getLogger(TelegramApiClient.class);

    private final TelegramProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public TelegramApiClient(TelegramProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean enabled() {
        return properties.isPollingEnabled() && properties.hasToken();
    }

    public JsonNode getUpdates(long offset) throws IOException, InterruptedException {
        Map<String, Object> body = Map.of(
                "offset", offset,
                "timeout", properties.getPollingTimeoutSeconds(),
                "allowed_updates", List.of("message", "callback_query")
        );
        JsonNode response = post("getUpdates", body);
        return response.path("result");
    }

    public void sendMessage(long chatId, String text, Object replyMarkup) {
        try {
            Map<String, Object> body = replyMarkup == null
                    ? Map.of("chat_id", chatId, "text", text)
                    : Map.of("chat_id", chatId, "text", text, "reply_markup", replyMarkup);
            post("sendMessage", body);
        } catch (Exception e) {
            LOG.warn("Telegram sendMessage failed: {}", e.getMessage(), e);
        }
    }

    public void answerCallbackQuery(String callbackQueryId) {
        if (callbackQueryId == null || callbackQueryId.isBlank()) {
            return;
        }
        try {
            post("answerCallbackQuery", Map.of("callback_query_id", callbackQueryId));
        } catch (Exception e) {
            LOG.debug("Telegram answerCallbackQuery failed: {}", e.getMessage());
        }
    }

    public void editMessageReplyMarkup(long chatId, long messageId, Object replyMarkup) {
        try {
            post("editMessageReplyMarkup", Map.of(
                    "chat_id", chatId,
                    "message_id", messageId,
                    "reply_markup", replyMarkup
            ));
        } catch (Exception e) {
            LOG.debug("Telegram editMessageReplyMarkup failed: {}", e.getMessage());
        }
    }

    private JsonNode post(String method, Object body) throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder(endpoint(method))
                .timeout(Duration.ofSeconds(properties.getPollingTimeoutSeconds() + 15L))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Telegram API HTTP " + response.statusCode() + ": " + response.body());
        }
        JsonNode root = objectMapper.readTree(response.body());
        if (!root.path("ok").asBoolean(false)) {
            throw new IOException("Telegram API returned ok=false: " + response.body());
        }
        return root;
    }

    private URI endpoint(String method) {
        String base = properties.getApiUrl().endsWith("/")
                ? properties.getApiUrl().substring(0, properties.getApiUrl().length() - 1)
                : properties.getApiUrl();
        String token = URLEncoder.encode(properties.getToken(), StandardCharsets.UTF_8);
        return URI.create(base + "/bot" + token + "/" + method);
    }
}
