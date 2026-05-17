package ru.qsystems.telegrambot;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import ru.qsystems.telegrambot.config.BranchConfigurationService;
import ru.qsystems.telegrambot.model.BranchConfig;
import ru.qsystems.telegrambot.model.BranchConnection;
import ru.qsystems.telegrambot.model.QueueSystem;
import ru.qsystems.telegrambot.queue.QueueGateway;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueueGatewayMetricsTest {

    @Test
    void createVisitRecordsSuccessMetric() {
        BranchConfigurationService branches = mock(BranchConfigurationService.class);
        HttpClient httpClient = mock(HttpClient.class);
        BlockingHttpClient blockingHttpClient = mock(BlockingHttpClient.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ObjectMapper objectMapper = new ObjectMapper();

        BranchConfig branch = new BranchConfig("1", "Main", "MN", "10", QueueSystem.ORCHESTRA, "", "", "", "");
        when(branches.connectionFor(branch)).thenReturn(new BranchConnection(QueueSystem.ORCHESTRA, "http://queue", "u", "p"));
        when(httpClient.toBlocking()).thenReturn(blockingHttpClient);
        when(blockingHttpClient.retrieve(org.mockito.ArgumentMatchers.<HttpRequest<?>>any(), eq(String.class)))
                .thenReturn("{\"ticket\":\"A001\"}");

        QueueGateway gateway = new QueueGateway(branches, httpClient, objectMapper, meterRegistry);

        Optional<Map<String, Object>> result = gateway.createVisit(branch, List.of("s1"), "u1", "User", Map.of("k", "v"));

        assertTrue(result.isPresent());
        assertEquals(1L, meterRegistry.find("bot.queue_gateway.request")
                .tags("operation", "create_visit", "queue_system", "orchestra", "result", "success")
                .timer()
                .count());
    }

    @Test
    void createVisitRecordsErrorMetric() {
        BranchConfigurationService branches = mock(BranchConfigurationService.class);
        HttpClient httpClient = mock(HttpClient.class);
        BlockingHttpClient blockingHttpClient = mock(BlockingHttpClient.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ObjectMapper objectMapper = new ObjectMapper();

        BranchConfig branch = new BranchConfig("2", "Main", "AX", "10", QueueSystem.AXIOMA, "", "", "", "");
        when(branches.connectionFor(branch)).thenReturn(new BranchConnection(QueueSystem.AXIOMA, "http://queue", "u", "p"));
        when(httpClient.toBlocking()).thenReturn(blockingHttpClient);
        when(blockingHttpClient.retrieve(org.mockito.ArgumentMatchers.<HttpRequest<?>>any(), eq(String.class)))
                .thenThrow(new RuntimeException("upstream down"));

        QueueGateway gateway = new QueueGateway(branches, httpClient, objectMapper, meterRegistry);

        Optional<Map<String, Object>> result = gateway.createVisit(branch, List.of("s1"), "u1", "User", Map.of());

        assertTrue(result.isEmpty());
        assertEquals(1L, meterRegistry.find("bot.queue_gateway.request")
                .tags("operation", "create_visit", "queue_system", "axioma", "result", "error")
                .timer()
                .count());
    }
}
