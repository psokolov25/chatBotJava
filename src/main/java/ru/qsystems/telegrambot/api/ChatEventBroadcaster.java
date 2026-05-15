package ru.qsystems.telegrambot.api;

import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class ChatEventBroadcaster {
    private final Map<String, ChatEventsWebSocket> sockets = new ConcurrentHashMap<>();

    public void register(String sessionId, ChatEventsWebSocket socket) {
        sockets.put(sessionId, socket);
    }

    public void unregister(String sessionId) {
        sockets.remove(sessionId);
    }

    public void broadcastToSession(String sessionId, String message) {
        ChatEventsWebSocket socket = sockets.get(sessionId);
        if (socket != null) {
            socket.send(message);
        }
    }
}
