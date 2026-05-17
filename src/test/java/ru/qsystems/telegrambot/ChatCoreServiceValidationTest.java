package ru.qsystems.telegrambot;

import org.junit.jupiter.api.Test;
import ru.qsystems.telegrambot.api.ChatCoreService;
import ru.qsystems.telegrambot.config.BranchConfigurationService;
import ru.qsystems.telegrambot.model.BranchConfig;
import ru.qsystems.telegrambot.model.QueueSystem;
import ru.qsystems.telegrambot.model.ServiceInfo;
import ru.qsystems.telegrambot.path.*;
import ru.qsystems.telegrambot.queue.QueueGateway;
import ru.qsystems.telegrambot.queue.ServiceFilter;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class ChatCoreServiceValidationTest {

    @Test
    void pathOptionWithInvalidIndexThrowsValidationError() {
        BranchConfigurationService branches = mock(BranchConfigurationService.class);
        QueueGateway queueGateway = mock(QueueGateway.class);
        ServiceFilter serviceFilter = mock(ServiceFilter.class);
        ClientPathService pathService = mock(ClientPathService.class);
        PathScriptExecutor scriptExecutor = mock(PathScriptExecutor.class);

        BranchConfig branch = new BranchConfig("1", "Main", "MN", "10", QueueSystem.ORCHESTRA, "", "", "", "");
        when(branches.branchSelectionFirst()).thenReturn(false);
        when(branches.singleBranchId()).thenReturn(Optional.of("1"));
        when(branches.byId("1")).thenReturn(Optional.of(branch));

        PathOption option = new PathOption("A", null, List.of("s1"), List.of(), MultiServicesAction.CHOOSE);
        PathQuestion question = new PathQuestion("q1", "Вопрос", List.of(option), false, PathInputType.OPTION, null, null, null);
        when(pathService.forBranch(branch)).thenReturn(Optional.of(new ClientPathConfig("q1", Map.of("q1", question))));
        when(serviceFilter.visibleServices(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(queueGateway.getServices(branch)).thenReturn(List.of());

        ChatCoreService service = new ChatCoreService(branches, queueGateway, serviceFilter, pathService, scriptExecutor);
        ChatCoreService.CoreResponse init = service.initialize(new ChatCoreService.InitRequest("v1", "User"));
        service.act(init.sessionId(), new ChatCoreService.CoreAction("take-ticket", null, null));

        assertThrows(IllegalArgumentException.class,
                () -> service.act(init.sessionId(), new ChatCoreService.CoreAction("path-option", "q1:not-number", null)));
    }

    @Test
    void pathInputWithBlankValueThrowsValidationError() {
        BranchConfigurationService branches = mock(BranchConfigurationService.class);
        QueueGateway queueGateway = mock(QueueGateway.class);
        ServiceFilter serviceFilter = mock(ServiceFilter.class);
        ClientPathService pathService = mock(ClientPathService.class);
        PathScriptExecutor scriptExecutor = mock(PathScriptExecutor.class);

        BranchConfig branch = new BranchConfig("1", "Main", "MN", "10", QueueSystem.ORCHESTRA, "", "", "", "");
        when(branches.branchSelectionFirst()).thenReturn(false);
        when(branches.singleBranchId()).thenReturn(Optional.of("1"));
        when(branches.byId("1")).thenReturn(Optional.of(branch));

        PathQuestion question = new PathQuestion("q1", "Введите данные", List.of(), false, PathInputType.TEXT, null, "answer", null);
        when(pathService.forBranch(branch)).thenReturn(Optional.of(new ClientPathConfig("q1", Map.of("q1", question))));
        when(serviceFilter.visibleServices(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(queueGateway.getServices(branch)).thenReturn(List.of());

        ChatCoreService service = new ChatCoreService(branches, queueGateway, serviceFilter, pathService, scriptExecutor);
        ChatCoreService.CoreResponse init = service.initialize(new ChatCoreService.InitRequest("v1", "User"));
        service.act(init.sessionId(), new ChatCoreService.CoreAction("take-ticket", null, null));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.act(init.sessionId(), new ChatCoreService.CoreAction("path-input", " ", null)));
        assertTrue(ex.getMessage().contains("path-input"));
    }

    @Test
    void selectServiceRejectsUnavailableServiceId() {
        BranchConfigurationService branches = mock(BranchConfigurationService.class);
        QueueGateway queueGateway = mock(QueueGateway.class);
        ServiceFilter serviceFilter = mock(ServiceFilter.class);
        ClientPathService pathService = mock(ClientPathService.class);
        PathScriptExecutor scriptExecutor = mock(PathScriptExecutor.class);

        BranchConfig branch = new BranchConfig("1", "Main", "MN", "10", QueueSystem.ORCHESTRA, "", "", "", "");
        when(branches.branchSelectionFirst()).thenReturn(false);
        when(branches.singleBranchId()).thenReturn(Optional.of("1"));
        when(branches.byId("1")).thenReturn(Optional.of(branch));
        when(pathService.forBranch(branch)).thenReturn(Optional.empty());
        when(serviceFilter.multiServiceEnabled(branch)).thenReturn(false);
        when(serviceFilter.visibleServices(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(queueGateway.getServices(branch)).thenReturn(List.of(new ServiceInfo("s1", "Service 1", Map.of())));

        ChatCoreService service = new ChatCoreService(branches, queueGateway, serviceFilter, pathService, scriptExecutor);
        ChatCoreService.CoreResponse init = service.initialize(new ChatCoreService.InitRequest("v1", "User"));
        service.act(init.sessionId(), new ChatCoreService.CoreAction("take-ticket", null, null));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.act(init.sessionId(), new ChatCoreService.CoreAction("select-service", "unknown", null)));
        assertTrue(ex.getMessage().contains("недоступна"));
    }

    @Test
    void selectServiceConfirmWithoutSelectionThrowsValidationError() {
        BranchConfigurationService branches = mock(BranchConfigurationService.class);
        QueueGateway queueGateway = mock(QueueGateway.class);
        ServiceFilter serviceFilter = mock(ServiceFilter.class);
        ClientPathService pathService = mock(ClientPathService.class);
        PathScriptExecutor scriptExecutor = mock(PathScriptExecutor.class);

        BranchConfig branch = new BranchConfig("1", "Main", "MN", "10", QueueSystem.ORCHESTRA, "", "", "", "");
        when(branches.branchSelectionFirst()).thenReturn(false);
        when(branches.singleBranchId()).thenReturn(Optional.of("1"));
        when(branches.byId("1")).thenReturn(Optional.of(branch));
        when(pathService.forBranch(branch)).thenReturn(Optional.empty());
        when(serviceFilter.multiServiceEnabled(branch)).thenReturn(true);
        when(serviceFilter.visibleServices(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(queueGateway.getServices(branch)).thenReturn(List.of(
                new ServiceInfo("s1", "Service 1", Map.of()),
                new ServiceInfo("s2", "Service 2", Map.of())
        ));

        ChatCoreService service = new ChatCoreService(branches, queueGateway, serviceFilter, pathService, scriptExecutor);
        ChatCoreService.CoreResponse init = service.initialize(new ChatCoreService.InitRequest("v1", "User"));
        service.act(init.sessionId(), new ChatCoreService.CoreAction("take-ticket", null, null));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.act(init.sessionId(), new ChatCoreService.CoreAction("select-service", "confirm", null)));
        assertTrue(ex.getMessage().contains("Не выбраны услуги"));
    }

    @Test
    void selectServiceConfirmRejectsStaleSelectedIds() {
        BranchConfigurationService branches = mock(BranchConfigurationService.class);
        QueueGateway queueGateway = mock(QueueGateway.class);
        ServiceFilter serviceFilter = mock(ServiceFilter.class);
        ClientPathService pathService = mock(ClientPathService.class);
        PathScriptExecutor scriptExecutor = mock(PathScriptExecutor.class);

        BranchConfig branch = new BranchConfig("1", "Main", "MN", "10", QueueSystem.ORCHESTRA, "", "", "", "");
        when(branches.branchSelectionFirst()).thenReturn(false);
        when(branches.singleBranchId()).thenReturn(Optional.of("1"));
        when(branches.byId("1")).thenReturn(Optional.of(branch));
        when(pathService.forBranch(branch)).thenReturn(Optional.empty());
        when(serviceFilter.multiServiceEnabled(branch)).thenReturn(true);
        when(serviceFilter.visibleServices(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(queueGateway.getServices(branch))
                .thenReturn(List.of(
                        new ServiceInfo("s1", "Service 1", Map.of()),
                        new ServiceInfo("s2", "Service 2", Map.of())
                ))
                .thenReturn(List.of(
                        new ServiceInfo("s1", "Service 1", Map.of()),
                        new ServiceInfo("s2", "Service 2", Map.of())
                ))
                .thenReturn(List.of(
                        new ServiceInfo("s2", "Service 2", Map.of())
                ));

        ChatCoreService service = new ChatCoreService(branches, queueGateway, serviceFilter, pathService, scriptExecutor);
        ChatCoreService.CoreResponse init = service.initialize(new ChatCoreService.InitRequest("v1", "User"));
        service.act(init.sessionId(), new ChatCoreService.CoreAction("take-ticket", null, null));
        service.act(init.sessionId(), new ChatCoreService.CoreAction("select-service", "s1", null));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.act(init.sessionId(), new ChatCoreService.CoreAction("select-service", "confirm", null)));
        assertTrue(ex.getMessage().contains("недоступна"));
    }
}
