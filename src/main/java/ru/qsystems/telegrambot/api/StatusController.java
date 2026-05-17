package ru.qsystems.telegrambot.api;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import ru.qsystems.telegrambot.config.BranchConfigurationService;
import ru.qsystems.telegrambot.config.TelegramProperties;
import ru.qsystems.telegrambot.telegram.UserStateStore;

import java.util.List;
import java.util.Map;

@Controller("/api/bot")
public class StatusController {
    private final BranchConfigurationService branches;
    private final TelegramProperties telegramProperties;
    private final UserStateStore userStateStore;

    public StatusController(BranchConfigurationService branches, TelegramProperties telegramProperties, UserStateStore userStateStore) {
        this.branches = branches;
        this.telegramProperties = telegramProperties;
        this.userStateStore = userStateStore;
    }

    @Get("/status")
    public Map<String, Object> status() {
        return Map.of(
                "telegramConfigured", telegramProperties.hasToken(),
                "branchCount", branches.branches().size(),
                "userStateCount", userStateStore.size(),
                "branches", branchViews()
        );
    }

    private List<Map<String, Object>> branchViews() {
        return branches.branches().stream()
                .map(branch -> Map.<String, Object>of(
                        "id", branch.branchId(),
                        "name", branch.name(),
                        "prefix", branch.prefix(),
                        "entryPointId", branch.entryPointId(),
                        "queueSystem", branch.queueSystem().name().toLowerCase()
                ))
                .toList();
    }
}
