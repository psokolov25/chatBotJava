package ru.qsystems.telegrambot.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.runtime.event.ApplicationShutdownEvent;
import io.micronaut.runtime.event.ApplicationStartupEvent;
import jakarta.inject.Singleton;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.qsystems.telegrambot.config.BranchConfigurationService;
import ru.qsystems.telegrambot.config.KafkaProperties;
import ru.qsystems.telegrambot.events.AxiomaEventNormalizer;
import ru.qsystems.telegrambot.events.VisitCallEventDispatcher;
import ru.qsystems.telegrambot.model.BranchConfig;
import ru.qsystems.telegrambot.model.BranchConnection;
import ru.qsystems.telegrambot.model.QueueSystem;
import ru.qsystems.telegrambot.util.JsonUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class AxiomaKafkaConsumerService implements ApplicationEventListener<ApplicationStartupEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(AxiomaKafkaConsumerService.class);

    private final KafkaProperties properties;
    private final BranchConfigurationService branches;
    private final AxiomaEventNormalizer normalizer;
    private final VisitCallEventDispatcher dispatcher;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "axioma-kafka");
        thread.setDaemon(false);
        return thread;
    });
    private final AtomicBoolean running = new AtomicBoolean(false);

    public AxiomaKafkaConsumerService(
            KafkaProperties properties,
            BranchConfigurationService branches,
            AxiomaEventNormalizer normalizer,
            VisitCallEventDispatcher dispatcher,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.branches = branches;
        this.normalizer = normalizer;
        this.dispatcher = dispatcher;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onApplicationEvent(ApplicationStartupEvent event) {
        if (!properties.isEnabled()) {
            LOG.info("Axioma Kafka consumer is disabled");
            return;
        }
        Map<String, String> serversByBranch = resolveServersByBranch();
        if (serversByBranch.isEmpty()) {
            LOG.info("No Axioma branches configured for Kafka events");
            return;
        }
        running.set(true);
        List<String> uniqueServerGroups = serversByBranch.values().stream().distinct().sorted().toList();
        int index = 1;
        for (String servers : uniqueServerGroups) {
            String groupId = properties.getGroupId() + "-" + index++;
            executor.submit(() -> consumeForever(servers, groupId));
        }
        LOG.info("Started {} Axioma Kafka consumer group(s)", uniqueServerGroups.size());
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

    private Map<String, String> resolveServersByBranch() {
        Map<String, String> perBranch = new LinkedHashMap<>();
        if (properties.getBranchBootstrapServers() != null) {
            perBranch.putAll(properties.getBranchBootstrapServers());
        }
        perBranch.putAll(JsonUtils.parseStringMap(objectMapper, properties.getBranchBootstrapServersJson()));
        Map<String, String> result = new LinkedHashMap<>();
        for (BranchConfig branch : branches.branches()) {
            BranchConnection connection = branches.connectionFor(branch);
            if (connection.queueSystem() != QueueSystem.AXIOMA) {
                continue;
            }
            String servers = firstNonBlank(perBranch.get(branch.branchId()), perBranch.get(branch.prefix()), properties.getBootstrapServers());
            if (servers != null && !servers.isBlank()) {
                result.put(branch.branchId(), servers);
            }
        }
        return result;
    }

    private void consumeForever(String bootstrapServers, String groupId) {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try (KafkaConsumer<String, String> consumer = createConsumer(bootstrapServers, groupId)) {
                consumer.subscribe(List.of(properties.getTopic()));
                LOG.info("Axioma Kafka consumer started. topic={} servers={} groupId={}", properties.getTopic(), bootstrapServers, groupId);
                while (running.get() && !Thread.currentThread().isInterrupted()) {
                    for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofSeconds(1))) {
                        handleRecord(record);
                    }
                }
            } catch (Exception e) {
                LOG.warn("Axioma Kafka consumer failed, reconnect in 5s: {}", e.getMessage(), e);
                sleep(5000);
            }
        }
    }

    private KafkaConsumer<String, String> createConsumer(String bootstrapServers, String groupId) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        return new KafkaConsumer<>(props);
    }

    private void handleRecord(ConsumerRecord<String, String> record) {
        try {
            Map<String, Object> payload = objectMapper.readValue(record.value(), new TypeReference<>() {});
            normalizer.normalize(payload).ifPresent(event -> dispatcher.dispatch(event.payload(), event.branchPrefix()));
        } catch (Exception e) {
            LOG.debug("Kafka event skipped: {}", e.getMessage());
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
