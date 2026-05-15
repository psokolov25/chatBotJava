package ru.qsystems.telegrambot.api;

import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Singleton
public class ChatEventBroadcaster {
    private final ConcurrentMap<String, Set<ChatEventsWebSocket>> sockets = new ConcurrentHashMap<>();

    public void register(String sessionId, ChatEventsWebSocket socket) {
        sockets.computeIfAbsent(sessionId, key -> ConcurrentHashMap.newKeySet()).add(socket);
    }

    public void unregister(String sessionId, ChatEventsWebSocket socket) {
        sockets.computeIfPresent(sessionId, (key, existing) -> {
            existing.remove(socket);
            return existing.isEmpty() ? null : existing;
        });
    }

    public void broadcastToSession(String sessionId, String message) {
        Set<ChatEventsWebSocket> sessionSockets = sockets.get(sessionId);
        if (sessionSockets == null || sessionSockets.isEmpty()) {
            return;
        }
        String payload = "{\"type\":\"visit-event\",\"message\":" + quoteJson(message)
                + ",\"timestamp\":\"" + Instant.now() + "\"}";
        for (ChatEventsWebSocket socket : sessionSockets) {
            socket.send(payload);
        }
    }

    private static String quoteJson(String value) {
        String safe = value == null ? "" : value;
        return '"' + safe
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r") + '"';
    }
}
