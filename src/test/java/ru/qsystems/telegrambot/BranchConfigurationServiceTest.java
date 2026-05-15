package ru.qsystems.telegrambot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.qsystems.telegrambot.config.BotRuntimeProperties;
import ru.qsystems.telegrambot.config.BranchConfigurationService;
import ru.qsystems.telegrambot.config.QueueProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BranchConfigurationServiceTest {
    @Test
    void parsesBranchesFromJson() {
        QueueProperties queue = new QueueProperties();
        queue.setBranchesJson("["
                + "{\"id\":1,\"name\":\"A\",\"prefix\":\"AA\",\"entry_point_id\":10},"
                + "{\"id\":2,\"name\":\"B\",\"prefix\":\"BB\",\"entry_point_id\":20}"
                + "]");
        BotRuntimeProperties runtime = new BotRuntimeProperties();

        BranchConfigurationService service = new BranchConfigurationService(queue, runtime, new ObjectMapper());

        assertEquals(2, service.branches().size());
        assertEquals("1", service.branches().get(0).branchId());
        assertEquals("BB", service.branches().get(1).prefix());
    }

    @Test
    void fallbackBranchIsCreatedWhenJsonIsEmpty() {
        QueueProperties queue = new QueueProperties();
        queue.setBranchId("6");
        queue.setBranchName("Главный");
        queue.setBranchCode("NTR");
        queue.setEntryPointId("2");
        BotRuntimeProperties runtime = new BotRuntimeProperties();
        runtime.setVisitCallTemplate("Шаблон");

        BranchConfigurationService service = new BranchConfigurationService(queue, runtime, new ObjectMapper());

        assertEquals(1, service.branches().size());
        assertEquals("Главный", service.branches().get(0).name());
        assertEquals("Шаблон", service.branches().get(0).visitCallTemplate());
    }

    @Test
    void duplicatePrefixesAreRejected() {
        QueueProperties queue = new QueueProperties();
        queue.setBranchesJson("["
                + "{\"id\":1,\"name\":\"A\",\"prefix\":\"AA\",\"entry_point_id\":10},"
                + "{\"id\":2,\"name\":\"B\",\"prefix\":\"AA\",\"entry_point_id\":20}"
                + "]");
        BotRuntimeProperties runtime = new BotRuntimeProperties();

        assertThrows(IllegalArgumentException.class, () -> new BranchConfigurationService(queue, runtime, new ObjectMapper()));
    }
}
