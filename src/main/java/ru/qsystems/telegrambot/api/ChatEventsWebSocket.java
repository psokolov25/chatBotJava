package ru.qsystems.telegrambot.api;

import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;

@ServerWebSocket("/ws/events/{sessionId}")
public class ChatEventsWebSocket {
    private final ChatEventBroadcaster broadcaster;
    private final ChatCoreService chatCoreService;
    private WebSocketSession session;
    private String sessionId;

    public ChatEventsWebSocket(ChatEventBroadcaster broadcaster, ChatCoreService chatCoreService) {
        this.broadcaster = broadcaster;
        this.chatCoreService = chatCoreService;
    }

    @OnOpen
    public void onOpen(String sessionId, WebSocketSession session) {
        if (!chatCoreService.sessionExists(sessionId)) {
            session.close();
            return;
        }
        this.sessionId = sessionId;
        this.session = session;
        broadcaster.register(sessionId, this);
        send("{\"type\":\"connected\",\"sessionId\":\"" + sessionId + "\"}");
    }

    @OnClose
    public void onClose() {
        if (sessionId != null) {
            broadcaster.unregister(sessionId, this);
        }
    }

    public void send(String payload) {
        if (session != null && session.isOpen()) {
            session.sendSync(payload);
        }
    }
}
