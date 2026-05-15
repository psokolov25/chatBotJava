package ru.qsystems.telegrambot.cometd;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.runtime.event.ApplicationShutdownEvent;
import io.micronaut.runtime.event.ApplicationStartupEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.qsystems.telegrambot.config.BranchConfigurationService;
import ru.qsystems.telegrambot.config.CometdProperties;
import ru.qsystems.telegrambot.events.VisitCallEventDispatcher;
import ru.qsystems.telegrambot.model.BranchConfig;
import ru.qsystems.telegrambot.model.BranchConnection;
import ru.qsystems.telegrambot.model.QueueSystem;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class CometdSubscriptionService implements ApplicationEventListener<ApplicationStartupEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(CometdSubscriptionService.class);

    private final CometdProperties properties;
    private final BranchConfigurationService branchConfigurationService;
    private final VisitCallEventDispatcher dispatcher;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "orchestra-cometd");
        thread.setDaemon(false);
        return thread;
    });

    public CometdSubscriptionService(
            CometdProperties properties,
            BranchConfigurationService branchConfigurationService,
            VisitCallEventDispatcher dispatcher,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.branchConfigurationService = branchConfigurationService;
        this.dispatcher = dispatcher;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onApplicationEvent(ApplicationStartupEvent event) {
        if (!properties.isEnabled()) {
            LOG.info("CometD subscriptions are disabled");
            return;
        }
        Map<ConnectionKey, List<String>> groupedChannels = new LinkedHashMap<>();
        for (BranchConfig branch : branchConfigurationService.branches()) {
            BranchConnection connection = branchConfigurationService.connectionFor(branch);
            if (connection.queueSystem() != QueueSystem.ORCHESTRA) {
                LOG.info("Skip CometD subscribe for branch {} ({}): queue system is {}",
                        branch.branchId(), branch.prefix(), connection.queueSystem());
                continue;
            }
            ConnectionKey key = new ConnectionKey(connection.normalizedBaseUrl(), connection.login(), connection.password());
            groupedChannels.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add("/events/" + branch.prefix() + "/QVoiceLight");
        }
        if (groupedChannels.isEmpty()) {
            LOG.info("No Orchestra branches configured for CometD subscriptions");
            return;
        }
        running.set(true);
        groupedChannels.forEach((key, channels) -> executor.submit(() -> runGroupForever(key, channels)));
        LOG.info("Started {} CometD group(s)", groupedChannels.size());
    }

    @io.micronaut.runtime.event.annotation.EventListener
    void onShutdown(ApplicationShutdownEvent event) {
        running.set(false);
        executor.shutdownNow();
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void runGroupForever(ConnectionKey key, List<String> channels) {
        long retryDelay = 2000;
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                runSession(key, channels);
                retryDelay = 2000;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LOG.warn("CometD group {} ended, reconnect in {}ms: {}", key.masked(), retryDelay, e.getMessage(), e);
                sleep(retryDelay);
                retryDelay = Math.min(retryDelay * 2, 60_000);
            }
        }
    }

    private void runSession(ConnectionKey key, List<String> channels) throws IOException, InterruptedException {
        URI cometdUri = URI.create(key.baseUrl() + "/cometd");
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .cookieHandler(new CookieManager())
                .build();

        String clientId = handshake(client, cometdUri, key);
        for (int i = 0; i < channels.size(); i++) {
            subscribe(client, cometdUri, key, clientId, channels.get(i), String.valueOf(i + 1));
        }
        for (int i = 0; i < channels.size(); i++) {
            publishInit(client, cometdUri, key, clientId, channels.get(i), String.valueOf(i + 2));
        }
        connectLoop(client, cometdUri, key, clientId, channels);
    }

    private String handshake(HttpClient client, URI cometdUri, ConnectionKey key) throws IOException, InterruptedException {
        List<Map<String, Object>> payload = List.of(Map.of(
                "channel", "/meta/handshake",
                "version", "1.0",
                "minimumVersion", "1.0",
                "supportedConnectionTypes", List.of("long-polling"),
                "id", "0"
        ));
        JsonNode response = postWithRetry(client, cometdUri, key, payload, Duration.ofSeconds(20), "handshake");
        JsonNode handshake = first(response);
        if (!handshake.path("successful").asBoolean(false)) {
            throw new IOException("Handshake failed: " + handshake.path("error").asText());
        }
        String clientId = handshake.path("clientId").asText(null);
        if (clientId == null || clientId.isBlank()) {
            throw new IOException("Handshake returned no clientId");
        }
        LOG.info("CometD handshake OK for {} clientId={}", key.masked(), clientId);
        return clientId;
    }

    private void subscribe(HttpClient client, URI cometdUri, ConnectionKey key, String clientId, String channel, String id)
            throws IOException, InterruptedException {
        List<Map<String, Object>> payload = List.of(Map.of(
                "channel", "/meta/subscribe",
                "clientId", clientId,
                "subscription", channel,
                "id", id
        ));
        JsonNode response = postWithRetry(client, cometdUri, key, payload, Duration.ofSeconds(20), "subscribe " + channel);
        if (!first(response).path("successful").asBoolean(false)) {
            throw new IOException("Subscribe failed: " + first(response).path("error").asText());
        }
        LOG.info("CometD subscribed: {}", channel);
    }

    private void publishInit(HttpClient client, URI cometdUri, ConnectionKey key, String clientId, String channel, String id)
            throws IOException, InterruptedException {
        String[] parts = channel.split("/");
        String branchPrefix = parts.length > 2 ? parts[2] : "";
        Map<String, Object> prm = Map.of("uid", branchPrefix + ":QVoiceLight", "type", 67, "encoding", "QP_JSON");
        Map<String, Object> c = Map.of("CMD", "INIT", "TGT", "CFM", "PRM", prm);
        Map<String, Object> publishData = Map.of("M", "C", "C", c, "N", "0");
        List<Map<String, Object>> payload = List.of(Map.of(
                "channel", properties.getInitChannel(),
                "clientId", clientId,
                "data", publishData,
                "id", id
        ));
        JsonNode response = postWithRetry(client, cometdUri, key, payload, Duration.ofSeconds(20), "publish INIT for " + branchPrefix);
        JsonNode first = first(response);
        if (!first.path("successful").asBoolean(false)) {
            LOG.warn("CometD INIT publish was not acknowledged for prefix {}: {}", branchPrefix,
                    first.path("error").asText("unknown error"));
            return;
        }
        LOG.info("CometD INIT published for prefix {}", branchPrefix);
    }

    private void connectLoop(HttpClient client, URI cometdUri, ConnectionKey key, String clientId, List<String> subscribedChannels)
            throws IOException, InterruptedException {
        long nextId = 3;
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            List<Map<String, Object>> payload = List.of(Map.of(
                    "channel", "/meta/connect",
                    "clientId", clientId,
                    "connectionType", "long-polling",
                    "id", String.valueOf(nextId++)
            ));
            JsonNode response = post(client, cometdUri, key, payload, Duration.ofSeconds(properties.getConnectTimeoutSeconds() + 10L));
            if (response.isObject()) {
                handleMessage(response, subscribedChannels);
            } else if (response.isArray()) {
                for (JsonNode msg : response) {
                    handleMessage(msg, subscribedChannels);
                }
            } else {
                throw new IOException("Unexpected CometD connect response: " + response);
            }
        }
    }

    private void handleMessage(JsonNode msg, List<String> subscribedChannels) throws IOException {
        String channel = msg.path("channel").asText("");
        if ("/meta/connect".equals(channel)) {
            if (!msg.path("successful").asBoolean(true)) {
                String error = msg.path("error").asText("");
                String reconnect = msg.path("advice").path("reconnect").asText("");
                if (error.contains("402::Unknown") || "handshake".equals(reconnect)) {
                    throw new IOException("Server requested re-handshake: " + error);
                }
            }
            return;
        }
        if (!subscribedChannels.contains(channel)) {
            return;
        }
        JsonNode data = msg.get("data");
        if (data == null || data.isNull()) {
            return;
        }
        Map<String, Object> payload;
        if (data.isTextual()) {
            payload = objectMapper.readValue(data.asText(), new TypeReference<>() {});
        } else {
            payload = objectMapper.convertValue(data, new TypeReference<>() {});
        }
        String[] parts = channel.split("/");
        String branchPrefix = parts.length > 2 ? parts[2] : null;
        dispatcher.dispatch(payload, branchPrefix);
    }

    private JsonNode post(HttpClient client, URI uri, ConnectionKey key, Object payload, Duration timeout)
            throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(payload);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .header("Authorization", basicAuth(key.login(), key.password()))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("CometD HTTP " + response.statusCode() + ": " + response.body());
        }
        return objectMapper.readTree(response.body());
    }

    private JsonNode postWithRetry(HttpClient client, URI uri, ConnectionKey key, Object payload, Duration timeout, String operation)
            throws IOException, InterruptedException {
        long retryDelayMs = 1_000;
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                return post(client, uri, key, payload, timeout);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (IOException e) {
                LOG.warn("CometD {} failed for {}, retry in {}ms: {}", operation, key.masked(), retryDelayMs, e.getMessage());
                sleep(retryDelayMs);
                retryDelayMs = Math.min(retryDelayMs * 2, 30_000);
            }
        }
        throw new IOException("CometD " + operation + " interrupted by shutdown");
    }

    private static JsonNode first(JsonNode node) throws IOException {
        if (node.isArray() && !node.isEmpty()) {
            return node.get(0);
        }
        throw new IOException("Unexpected CometD response: " + node);
    }

    private static String basicAuth(String login, String password) {
        String value = (login == null ? "" : login) + ":" + (password == null ? "" : password);
        return "Basic " + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record ConnectionKey(String baseUrl, String login, String password) {
        String masked() {
            return baseUrl + "#" + login;
        }
    }
}
