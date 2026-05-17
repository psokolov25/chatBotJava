package ru.qsystems.telegrambot;

import org.junit.jupiter.api.Test;
import ru.qsystems.telegrambot.api.ChatCoreService;
import ru.qsystems.telegrambot.api.ChatEventBroadcaster;
import ru.qsystems.telegrambot.config.BranchConfigurationService;
import ru.qsystems.telegrambot.events.VisitCallEventDispatcher;
import ru.qsystems.telegrambot.events.VisitMessageRenderer;
import ru.qsystems.telegrambot.model.BranchConfig;
import ru.qsystems.telegrambot.model.QueueSystem;
import ru.qsystems.telegrambot.telegram.TelegramApiClient;
import ru.qsystems.telegrambot.telegram.UserStateStore;
import ru.qsystems.telegrambot.util.PiiSanitizer;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VisitCallEventDispatcherTest {

    @Test
    void dispatchPropagatesGeneratedRequestIdToWebsocketBroadcast() {
        TelegramApiClient telegram = mock(TelegramApiClient.class);
        UserStateStore userStateStore = new UserStateStore();
        BranchConfigurationService branches = mock(BranchConfigurationService.class);
        VisitMessageRenderer renderer = mock(VisitMessageRenderer.class);
        PiiSanitizer sanitizer = mock(PiiSanitizer.class);
        ChatEventBroadcaster broadcaster = mock(ChatEventBroadcaster.class);
        ChatCoreService chatCoreService = mock(ChatCoreService.class);

        BranchConfig branch = new BranchConfig("1", "Main", "MN", "10", QueueSystem.ORCHESTRA, "", "", "", "Ticket {ticketId}");
        when(branches.byPrefix("MN")).thenReturn(Optional.of(branch));
        when(renderer.render(anyString(), anyMap(), anyMap())).thenReturn("Ticket A001");
        when(sanitizer.sanitize(anyMap())).thenReturn(Map.of());
        when(chatCoreService.sessionForVisitor("visitor-1")).thenReturn(Optional.of("session-1"));

        VisitCallEventDispatcher dispatcher = new VisitCallEventDispatcher(
                telegram, userStateStore, branches, renderer, sanitizer, broadcaster, chatCoreService
        );

        Map<String, Object> payload = Map.of(
                "E", Map.of(
                        "evnt", "VISIT_CALL",
                        "prm", Map.of(
                                "TelegramChatId", "100",
                                "TelegramCustomerId", "visitor-1",
                                "ticketId", "A001"
                        )
                )
        );

        dispatcher.dispatch(payload, "MN");

        verify(telegram).sendMessage(100L, "Ticket A001", null);
        verify(broadcaster).broadcastToSession(eq("session-1"), eq("Ticket A001"), argThat(id -> id != null && !id.isBlank()));
    }

    @Test
    void dispatchSkipsTelegramWhenBranchPrefixNotSubscribedByUser() {
        TelegramApiClient telegram = mock(TelegramApiClient.class);
        UserStateStore userStateStore = new UserStateStore();
        BranchConfigurationService branches = mock(BranchConfigurationService.class);
        VisitMessageRenderer renderer = mock(VisitMessageRenderer.class);
        PiiSanitizer sanitizer = mock(PiiSanitizer.class);
        ChatEventBroadcaster broadcaster = mock(ChatEventBroadcaster.class);
        ChatCoreService chatCoreService = mock(ChatCoreService.class);

        userStateStore.addBranchSubscription(100L, "OTHER");

        BranchConfig branch = new BranchConfig("1", "Main", "MN", "10", QueueSystem.ORCHESTRA, "", "", "", "Ticket {ticketId}");
        when(branches.byPrefix("MN")).thenReturn(Optional.of(branch));
        when(renderer.render(anyString(), anyMap(), anyMap())).thenReturn("Ticket A001");
        when(sanitizer.sanitize(anyMap())).thenReturn(Map.of());

        VisitCallEventDispatcher dispatcher = new VisitCallEventDispatcher(
                telegram, userStateStore, branches, renderer, sanitizer, broadcaster, chatCoreService
        );

        Map<String, Object> payload = Map.of(
                "E", Map.of(
                        "evnt", "VISIT_CALL",
                        "prm", Map.of(
                                "TelegramChatId", "100",
                                "TelegramCustomerId", "visitor-1",
                                "ticketId", "A001"
                        )
                )
        );

        dispatcher.dispatch(payload, "MN");

        verifyNoInteractions(telegram);
        verifyNoInteractions(broadcaster);
    }
}
