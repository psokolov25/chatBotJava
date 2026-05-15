package ru.qsystems.telegrambot.api;

import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;

@ServerWebSocket("/ws/events/{sessionId}")
public class ChatEventsWebSocket {
    private final ChatEventBroadcaster broadcaster;
    private WebSocketSession session;
    private String sessionId;

    public ChatEventsWebSocket(ChatEventBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @OnOpen
    public void onOpen(String sessionId, WebSocketSession session) {
        this.sessionId = sessionId;
        this.session = session;
        broadcaster.register(sessionId, this);
    }

    @OnClose
    public void onClose() {
        if (sessionId != null) {
            broadcaster.unregister(sessionId);
        }
    }

    public void send(String payload) {
        if (session != null && session.isOpen()) {
            session.sendSync(payload);
        }
    }
}
