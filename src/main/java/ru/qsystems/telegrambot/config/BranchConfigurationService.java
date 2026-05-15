package ru.qsystems.telegrambot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import ru.qsystems.telegrambot.model.BranchConfig;
import ru.qsystems.telegrambot.model.BranchConnection;
import ru.qsystems.telegrambot.model.QueueSystem;
import ru.qsystems.telegrambot.util.JsonUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Singleton
public class BranchConfigurationService {
    private final QueueProperties queueProperties;
    private final BotRuntimeProperties runtimeProperties;
    private final List<BranchConfig> branches;
    private final Map<String, BranchConfig> branchesById;

    public BranchConfigurationService(
            QueueProperties queueProperties,
            BotRuntimeProperties runtimeProperties,
            ObjectMapper objectMapper
    ) {
        this.queueProperties = queueProperties;
        this.runtimeProperties = runtimeProperties;
        this.branches = Collections.unmodifiableList(parseBranches(objectMapper));
        Map<String, BranchConfig> byId = new LinkedHashMap<>();
        for (BranchConfig branch : this.branches) {
            byId.put(branch.branchId(), branch);
        }
        this.branchesById = Collections.unmodifiableMap(byId);
    }

    public List<BranchConfig> branches() {
        return branches;
    }

    public Map<String, BranchConfig> branchesById() {
        return branchesById;
    }

    public Optional<BranchConfig> byId(String branchId) {
        return Optional.ofNullable(branchesById.get(branchId));
    }

    public Optional<BranchConfig> byPrefix(String prefix) {
        return branches.stream()
                .filter(branch -> branch.prefix().equals(prefix))
                .findFirst();
    }

    public Optional<String> singleBranchId() {
        return branches.size() == 1 ? Optional.of(branches.get(0).branchId()) : Optional.empty();
    }

    public BranchConnection connectionFor(BranchConfig branch) {
        QueueSystem global = QueueSystem.from(queueProperties.getSystem(), QueueSystem.ORCHESTRA);
        QueueSystem queueSystem = branch.queueSystem() == null ? global : branch.queueSystem();
        String baseUrl = firstNonBlank(branch.baseUrl(), queueSystem == QueueSystem.AXIOMA
                ? queueProperties.getAxiomaUrl()
                : queueProperties.getOrchestraUrl());
        String login = firstNonBlank(branch.login(), queueSystem == QueueSystem.AXIOMA
                ? queueProperties.getAxiomaLogin()
                : queueProperties.getOrchestraLogin());
        String password = firstNonBlank(branch.password(), queueSystem == QueueSystem.AXIOMA
                ? queueProperties.getAxiomaPassword()
                : queueProperties.getOrchestraPassword());
        return new BranchConnection(queueSystem, baseUrl, login, password);
    }

    public boolean branchSelectionFirst() {
        String value = runtimeProperties.getFlowOrder() == null ? "" : runtimeProperties.getFlowOrder().trim().toUpperCase();
        return value.equals("BRANCH_FIRST") || value.equals("BRANCH_THEN_ACTION");
    }

    private List<BranchConfig> parseBranches(ObjectMapper objectMapper) {
        Map<String, String> templateOverrides = resolveTemplateOverrides(objectMapper);
        List<BranchConfig> result = new ArrayList<>();
        String raw = queueProperties.getBranchesJson();
        QueueSystem defaultSystem = QueueSystem.from(queueProperties.getSystem(), QueueSystem.ORCHESTRA);

        List<Map<String, Object>> parsed;
        if (raw != null && !raw.isBlank()) {
            parsed = JsonUtils.parseListOfObjects(objectMapper, raw);
        } else {
            parsed = queueProperties.getBranches();
        }

        if (parsed != null && !parsed.isEmpty()) {
            for (Map<String, Object> item : parsed) {
                String id = required(item, "id");
                String prefix = required(item, "prefix");
                BranchConfig branch = new BranchConfig(
                        id,
                        required(item, "name"),
                        prefix,
                        required(item, "entry_point_id"),
                        QueueSystem.from(asString(item.get("queue_system")), defaultSystem),
                        blankToNull(asString(item.get("base_url"))),
                        blankToNull(asString(item.get("login"))),
                        blankToNull(asString(item.get("password"))),
                        blankToNull(asString(item.get("visit_call_template")))
                );
                result.add(applyTemplateOverride(branch, templateOverrides));
            }
        } else {
            BranchConfig fallback = new BranchConfig(
                    queueProperties.getBranchId(),
                    queueProperties.getBranchName(),
                    queueProperties.getBranchCode(),
                    queueProperties.getEntryPointId(),
                    defaultSystem,
                    null,
                    null,
                    null,
                    runtimeProperties.getVisitCallTemplate()
            );
            result.add(applyTemplateOverride(fallback, templateOverrides));
        }

        validate(result);
        return result;
    }

    private Map<String, String> resolveTemplateOverrides(ObjectMapper objectMapper) {
        Map<String, String> overrides = new LinkedHashMap<>();
        if (runtimeProperties.getBranchVisitCallTemplates() != null) {
            overrides.putAll(runtimeProperties.getBranchVisitCallTemplates());
        }
        overrides.putAll(JsonUtils.parseStringMap(objectMapper, runtimeProperties.getBranchVisitCallTemplatesJson()));
        return overrides;
    }

    private BranchConfig applyTemplateOverride(BranchConfig branch, Map<String, String> overrides) {
        String override = overrides.get(branch.branchId());
        if (override == null) {
            override = overrides.get(branch.prefix());
        }
        return override == null ? branch : branch.withVisitCallTemplate(override);
    }

    private void validate(List<BranchConfig> branches) {
        Set<String> ids = new LinkedHashSet<>();
        Set<String> prefixes = new LinkedHashSet<>();
        for (BranchConfig branch : branches) {
            if (!ids.add(branch.branchId())) {
                throw new IllegalArgumentException("ORCHESTRA_BRANCHES contains duplicate branch ids: " + branch.branchId());
            }
            if (!prefixes.add(branch.prefix())) {
                throw new IllegalArgumentException("ORCHESTRA_BRANCHES contains duplicate prefixes: " + branch.prefix());
            }
        }
    }

    private static String required(Map<String, Object> item, String key) {
        String value = asString(item.get(key));
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Branch item must contain non-empty field: " + key);
        }
        return value;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String firstNonBlank(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }
}
