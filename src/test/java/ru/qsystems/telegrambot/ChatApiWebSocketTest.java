package ru.qsystems.telegrambot;

import io.micronaut.http.HttpResponse;
import io.micronaut.websocket.WebSocketSession;
import org.junit.jupiter.api.Test;
import ru.qsystems.telegrambot.api.ChatCoreController;
import ru.qsystems.telegrambot.api.ChatCoreService;
import ru.qsystems.telegrambot.api.ChatEventBroadcaster;
import ru.qsystems.telegrambot.api.ChatEventsWebSocket;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ChatApiWebSocketTest {

    @Test
    void restInitReturnsWsEndpointFromService() {
        ChatCoreService service = mock(ChatCoreService.class);
        ChatCoreController controller = new ChatCoreController(service);
        ChatCoreService.CoreResponse expected = new ChatCoreService.CoreResponse(
                "session-1", "visitor-1", "ok", List.of(), false, null, "/ws/events/session-1"
        );
        when(service.initialize(any())).thenReturn(expected);

        ChatCoreService.CoreResponse actual = controller.init(new ChatCoreService.InitRequest("visitor-1", "Иван"));

        assertEquals("session-1", actual.sessionId());
        assertEquals("/ws/events/session-1", actual.wsEndpoint());
    }

    @Test
    void restActionSupportsLegacyActionField() {
        ChatCoreService service = mock(ChatCoreService.class);
        ChatCoreController controller = new ChatCoreController(service);
        when(service.act(eq("s-1"), any())).thenReturn(new ChatCoreService.CoreResponse("s-1", "v-1", "ok", List.of(), false, null, "/ws/events/s-1"));

        controller.action("s-1", new ChatCoreController.ActionRequest("select-service:123", null, null, "Пользователь"));

        verify(service).act(eq("s-1"), argThat(action ->
                "select-service".equals(action.type())
                        && "123".equals(action.value())
                        && "Пользователь".equals(action.customerName())
        ));
    }

    @Test
    void restBadRequestHandlerReturns400() {
        ChatCoreController controller = new ChatCoreController(mock(ChatCoreService.class));

        HttpResponse<Map<String, String>> response = controller.onBadRequest(new IllegalArgumentException("bad"));

        assertEquals(400, response.code());
        assertEquals("bad", response.body().get("message"));
    }

    @Test
    void broadcasterSendsToAllSocketsInSession() {
        ChatEventBroadcaster broadcaster = new ChatEventBroadcaster();
        ChatEventsWebSocket socket1 = mock(ChatEventsWebSocket.class);
        ChatEventsWebSocket socket2 = mock(ChatEventsWebSocket.class);

        broadcaster.register("session-1", socket1);
        broadcaster.register("session-1", socket2);
        broadcaster.broadcastToSession("session-1", "Талон A001");

        verify(socket1).send(contains("\"type\":\"visit-event\""));
        verify(socket2).send(contains("\"type\":\"visit-event\""));
        verify(socket1).send(contains("Талон A001"));
        verify(socket2).send(contains("Талон A001"));
    }

    @Test
    void websocketRegistersAndUnregistersKnownSession() {
        ChatEventBroadcaster broadcaster = mock(ChatEventBroadcaster.class);
        ChatCoreService service = mock(ChatCoreService.class);
        ChatEventsWebSocket socket = new ChatEventsWebSocket(broadcaster, service);
        WebSocketSession session = mock(WebSocketSession.class);
        when(service.sessionExists("session-1")).thenReturn(true);
        when(session.isOpen()).thenReturn(true);

        socket.onOpen("session-1", session);
        socket.onClose();

        verify(broadcaster).register("session-1", socket);
        verify(session).sendSync(contains("\"connected\""));
        verify(broadcaster).unregister("session-1", socket);
        verify(session, never()).close();
    }

    @Test
    void websocketClosesUnknownSession() {
        ChatEventBroadcaster broadcaster = mock(ChatEventBroadcaster.class);
        ChatCoreService service = mock(ChatCoreService.class);
        ChatEventsWebSocket socket = new ChatEventsWebSocket(broadcaster, service);
        WebSocketSession session = mock(WebSocketSession.class);
        when(service.sessionExists("missing")).thenReturn(false);

        socket.onOpen("missing", session);

        verify(session).close();
        verify(broadcaster, never()).register(any(), any());
    }
}
