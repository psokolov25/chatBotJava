package ru.qsystems.telegrambot;

import org.junit.jupiter.api.Test;
import ru.qsystems.telegrambot.api.ChatCoreService;
import ru.qsystems.telegrambot.config.BotRuntimeProperties;
import ru.qsystems.telegrambot.config.BranchConfigurationService;
import ru.qsystems.telegrambot.model.BranchConfig;
import ru.qsystems.telegrambot.model.QueueSystem;
import ru.qsystems.telegrambot.model.ServiceInfo;
import ru.qsystems.telegrambot.path.ClientPathService;
import ru.qsystems.telegrambot.path.CustomPathElementService;
import ru.qsystems.telegrambot.path.PathScriptExecutor;
import ru.qsystems.telegrambot.path.ScriptPackageRegistry;
import ru.qsystems.telegrambot.queue.QueueGateway;
import ru.qsystems.telegrambot.queue.ServiceFilter;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MainClientPathDemoFlowTest {

    @Test
    void mainClientPathYmlSupportsAllThreeDemoEntryFlows() {
        BranchConfigurationService branches = mock(BranchConfigurationService.class);
        QueueGateway queueGateway = mock(QueueGateway.class);
        ServiceFilter serviceFilter = mock(ServiceFilter.class);

        BotRuntimeProperties props = new BotRuntimeProperties();
        props.setClientPathYaml("client_path.yml");
        props.setScriptPackagesDir("/definitely/not/exist");
        ClientPathService pathService = new ClientPathService(props, new CustomPathElementService());
        PathScriptExecutor scriptExecutor = new PathScriptExecutor(new ScriptPackageRegistry(props));

        BranchConfig branch = new BranchConfig("1", "Main", "MN", "10", QueueSystem.ORCHESTRA, "", "", "", "");
        when(branches.branchSelectionFirst()).thenReturn(true);
        when(branches.branches()).thenReturn(List.of(branch, new BranchConfig("2", "Alt", "AL", "20", QueueSystem.ORCHESTRA, "", "", "", "")));
        when(branches.singleBranchId()).thenReturn(Optional.empty());
        when(branches.byId("1")).thenReturn(Optional.of(branch));

        List<ServiceInfo> services = List.of(
                new ServiceInfo("s-legal", "Юридическая консультация", Map.of()),
                new ServiceInfo("s-docs", "Оформление документов", Map.of()),
                new ServiceInfo("s-prereg", "Предварительная запись", Map.of()),
                new ServiceInfo("s-premium", "Премиальное обслуживание", Map.of())
        );
        when(queueGateway.getServices(branch)).thenReturn(services);
        when(serviceFilter.visibleServices(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(serviceFilter.multiServiceEnabled(branch)).thenReturn(true);
        when(queueGateway.createVisit(eq(branch), anyList(), anyString(), anyString(), anyMap()))
                .thenReturn(Optional.of(Map.of("ticket", "T-100")));

        ChatCoreService service = new ChatCoreService(branches, queueGateway, serviceFilter, pathService, scriptExecutor);

        ChatCoreService.CoreResponse init = service.initialize(new ChatCoreService.InitRequest("visitor", "User"));
        assertEquals("Сначала выберите отделение:", init.message());
        ChatCoreService.CoreResponse afterBranch = service.act(init.sessionId(), new ChatCoreService.CoreAction("select-branch", "1", null));
        List<String> actions = afterBranch.options().stream().map(ChatCoreService.CoreOption::action).toList();
        assertTrue(actions.contains("take-ticket"));
        assertTrue(actions.contains("take-ticket-prereg"));
        assertTrue(actions.contains("take-ticket-segment"));

        ChatCoreService.CoreResponse standardStart = service.act(init.sessionId(), new ChatCoreService.CoreAction("take-ticket", null, null));
        assertEquals("Выберите формат обращения", standardStart.message());

        ChatCoreService.CoreResponse preregStart = service.act(init.sessionId(), new ChatCoreService.CoreAction("take-ticket-prereg", null, null));
        assertEquals("Введите номер предзаписи", preregStart.message());
        ChatCoreService.CoreResponse preregStep2 = service.act(init.sessionId(), new ChatCoreService.CoreAction("path-input", "ABCD1234", null));
        assertEquals("Введите ПИН из SMS", preregStep2.message());
        ChatCoreService.CoreResponse preregTicket = service.act(init.sessionId(), new ChatCoreService.CoreAction("path-input", "1234", null));
        assertTrue(preregTicket.message().contains("Ваш талон"));

        ChatCoreService.CoreResponse segmentStart = service.act(init.sessionId(), new ChatCoreService.CoreAction("take-ticket-segment", null, null));
        assertEquals("Введите номер телефона", segmentStart.message());
        ChatCoreService.CoreResponse segmentChoice = service.act(init.sessionId(), new ChatCoreService.CoreAction("path-input", "+7(900)111-2200", null));
        assertTrue(segmentChoice.message().contains("несколько услуг"));
    }
}
