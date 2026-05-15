package ru.qsystems.telegrambot.queue;

import jakarta.inject.Singleton;
import ru.qsystems.telegrambot.config.BotRuntimeProperties;
import ru.qsystems.telegrambot.model.BranchConfig;
import ru.qsystems.telegrambot.model.ServiceInfo;
import ru.qsystems.telegrambot.util.JsonUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class ServiceFilter {
    private final Set<String> blacklist;
    private final boolean globalMultiServiceEnabled;
    private final Map<String, Boolean> branchMultiServiceEnabled;

    public ServiceFilter(BotRuntimeProperties properties, ObjectMapper objectMapper) {
        this.blacklist = splitBlacklist(properties.getServiceBlacklist());
        this.globalMultiServiceEnabled = properties.isMultiServiceEnabled();
        this.branchMultiServiceEnabled = new java.util.LinkedHashMap<>();
        if (properties.getBranchMultiServiceEnabled() != null) {
            this.branchMultiServiceEnabled.putAll(properties.getBranchMultiServiceEnabled());
        }
        this.branchMultiServiceEnabled.putAll(JsonUtils.parseBooleanMap(objectMapper, properties.getBranchMultiServiceEnabledJson()));
    }

    public List<ServiceInfo> visibleServices(List<ServiceInfo> services) {
        return services.stream()
                .filter(service -> !blacklist.contains(service.name()))
                .toList();
    }

    public boolean multiServiceEnabled(BranchConfig branch) {
        Boolean branchValue = branchMultiServiceEnabled.get(branch.branchId());
        if (branchValue == null) {
            branchValue = branchMultiServiceEnabled.get(branch.prefix());
        }
        return branchValue == null ? globalMultiServiceEnabled : branchValue;
    }

    private static Set<String> splitBlacklist(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return List.of(raw.split(",")).stream()
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .collect(Collectors.toSet());
    }
}
