package ru.qsystems.telegrambot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import ru.qsystems.telegrambot.api.StatusController;
import ru.qsystems.telegrambot.config.BranchConfigurationService;
import ru.qsystems.telegrambot.config.TelegramProperties;
import ru.qsystems.telegrambot.model.BranchConfig;
import ru.qsystems.telegrambot.model.QueueSystem;
import ru.qsystems.telegrambot.path.ClientPathService;
import ru.qsystems.telegrambot.queue.QueueGateway;
import ru.qsystems.telegrambot.queue.ServiceFilter;
import ru.qsystems.telegrambot.telegram.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatBotControllersTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void statusControllerReturnsConfiguredBranchView() {
        BranchConfigurationService branches = mock(BranchConfigurationService.class);
        TelegramProperties telegramProperties = new TelegramProperties();
        telegramProperties.setToken("token");

        when(branches.branches()).thenReturn(List.of(
                new BranchConfig("1", "Main", "MN", "10", QueueSystem.ORCHESTRA, "http://q", "u", "p", "tpl")
        ));

        StatusController controller = new StatusController(branches, telegramProperties);
        Map<String, Object> response = controller.status();

        assertEquals(true, response.get("telegramConfigured"));
        assertEquals(1, response.get("branchCount"));
        List<?> list = (List<?>) response.get("branches");
        assertEquals(1, list.size());
        Map<?, ?> branch = (Map<?, ?>) list.get(0);
        assertEquals("1", branch.get("id"));
        assertEquals("orchestra", branch.get("queueSystem"));
    }

    @Test
    void telegramHandlerStartSendsWelcomeAndBranchSelection() {
        TelegramApiClient telegram = mock(TelegramApiClient.class);
        BranchConfigurationService branches = mock(BranchConfigurationService.class);
        QueueGateway queueGateway = mock(QueueGateway.class);
        ServiceFilter serviceFilter = mock(ServiceFilter.class);
        KeyboardFactory keyboardFactory = mock(KeyboardFactory.class);
        UserStateStore stateStore = new UserStateStore();
        ClientPathService pathService = mock(ClientPathService.class);

        when(branches.branchSelectionFirst()).thenReturn(true);
        when(branches.branches()).thenReturn(List.of(
                new BranchConfig("1", "Main", "MN", "10", QueueSystem.ORCHESTRA, "", "", "", ""),
                new BranchConfig("2", "Second", "SC", "20", QueueSystem.AXIOMA, "", "", "", "")
        ));
        when(keyboardFactory.chooseBranchButton()).thenReturn(Map.of("inline_keyboard", List.of()));

        TelegramUpdateHandler handler = new TelegramUpdateHandler(
                telegram, branches, queueGateway, serviceFilter, keyboardFactory, stateStore, pathService
        );

        ObjectNode update = MAPPER.createObjectNode();
        ObjectNode message = update.putObject("message");
        message.putObject("chat").put("id", 77);
        message.putObject("from").put("id", 77);
        message.put("text", "/start");

        handler.handle(update);

        verify(telegram).sendMessage(eq(77L), eq("Добро пожаловать!"), isNull());
        verify(telegram).sendMessage(eq(77L), eq("Сначала выберите отделение:"), any());
    }

    @Test
    void telegramHandlerUnknownBranchCallbackResetsConversation() {
        TelegramApiClient telegram = mock(TelegramApiClient.class);
        BranchConfigurationService branches = mock(BranchConfigurationService.class);
        QueueGateway queueGateway = mock(QueueGateway.class);
        ServiceFilter serviceFilter = mock(ServiceFilter.class);
        KeyboardFactory keyboardFactory = mock(KeyboardFactory.class);
        UserStateStore stateStore = new UserStateStore();
        ClientPathService pathService = mock(ClientPathService.class);

        when(branches.byId("404")).thenReturn(java.util.Optional.empty());

        TelegramUpdateHandler handler = new TelegramUpdateHandler(
                telegram, branches, queueGateway, serviceFilter, keyboardFactory, stateStore, pathService
        );

        UserState state = stateStore.get(55L);
        state.data().put("branch_id", "old");

        ObjectNode update = MAPPER.createObjectNode();
        ObjectNode cb = update.putObject("callback_query");
        cb.put("id", "cb-1");
        cb.put("data", "branch:404");
        cb.putObject("from").put("id", 55);
        ObjectNode message = cb.putObject("message");
        message.put("message_id", 10);
        message.putObject("chat").put("id", 55);

        handler.handle(update);

        verify(telegram).answerCallbackQuery("cb-1");
        verify(telegram).sendMessage(eq(55L), eq("Не удалось выбрать отделение"), isNull());
        assertTrue(state.data().isEmpty());
    }
}
