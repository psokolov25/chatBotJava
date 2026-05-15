package ru.qsystems.telegrambot.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import ru.qsystems.telegrambot.util.Env;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties("bot.kafka")
public class KafkaProperties {
    private boolean enabled = true;
    private String topic = "events";
    private String groupId = "telegram-queue-bot";
    private String bootstrapServers = "192.168.8.40:29092";
    private String branchBootstrapServersJson = "";
    private Map<String, String> branchBootstrapServers = new LinkedHashMap<>();

    public boolean isEnabled() { return Env.bool("AXIOMA_KAFKA_ENABLED", enabled); }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getTopic() { return Env.string("AXIOMA_KAFKA_EVENTS_TOPIC", topic); }
    public void setTopic(String topic) { this.topic = topic; }
    public String getGroupId() { return Env.string("AXIOMA_KAFKA_GROUP_ID", groupId); }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getBootstrapServers() { return Env.string("AXIOMA_KAFKA_BOOTSTRAP_SERVERS", bootstrapServers); }
    public void setBootstrapServers(String bootstrapServers) { this.bootstrapServers = bootstrapServers; }
    public String getBranchBootstrapServersJson() { return Env.string("ORCHESTRA_BRANCH_KAFKA_SERVERS", branchBootstrapServersJson); }
    public void setBranchBootstrapServersJson(String branchBootstrapServersJson) { this.branchBootstrapServersJson = branchBootstrapServersJson; }
    public Map<String, String> getBranchBootstrapServers() { return branchBootstrapServers; }
    public void setBranchBootstrapServers(Map<String, String> branchBootstrapServers) {
        this.branchBootstrapServers = branchBootstrapServers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(branchBootstrapServers);
    }
}
