package ru.qsystems.telegrambot.path;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.micronaut.context.annotation.Context;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.qsystems.telegrambot.config.BotRuntimeProperties;
import ru.qsystems.telegrambot.model.BranchConfig;
import ru.qsystems.telegrambot.model.ServiceInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Context
@Singleton
public class ClientPathService {
    private static final Logger LOG = LoggerFactory.getLogger(ClientPathService.class);

    private final Map<String, ClientPathConfig> pathsByBranchKey;
    private final CustomPathElementService customPathElementService;

    public ClientPathService(BotRuntimeProperties runtimeProperties, CustomPathElementService customPathElementService) {
        this.customPathElementService = customPathElementService;
        this.pathsByBranchKey = Collections.unmodifiableMap(load(runtimeProperties.getClientPathYaml()));
    }

    public Optional<ClientPathConfig> forBranch(BranchConfig branch) {
        if (branch == null) {
            return Optional.empty();
        }
        ClientPathConfig config = pathsByBranchKey.get(branch.branchId());
        if (config == null) {
            config = pathsByBranchKey.get(branch.prefix());
        }
        if (config == null) {
            config = pathsByBranchKey.get(branch.name());
        }
        if (config == null) {
            config = pathsByBranchKey.get("default");
        }
        return Optional.ofNullable(config);
    }

    public List<String> optionServiceIds(PathOption option, List<ServiceInfo> services) {
        if (option.serviceIds() != null && !option.serviceIds().isEmpty()) {
            return option.serviceIds();
        }
        if (option.serviceNames() == null || option.serviceNames().isEmpty()) {
            return List.of();
        }
        Set<String> names = option.serviceNames().stream()
                .map(ClientPathService::normalizeServiceName)
                .collect(Collectors.toSet());
        return services.stream()
                .filter(service -> names.contains(normalizeServiceName(service.name())))
                .map(ServiceInfo::id)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, ClientPathConfig> load(String pathText) {
        Path path = Path.of(pathText == null || pathText.isBlank() ? "client_path.yml" : pathText);
        if (!Files.exists(path)) {
            LOG.info("Client path config is not found: {}", path);
            return Map.of();
        }
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        try {
            Map<String, Object> root = yamlMapper.readValue(path.toFile(), new TypeReference<>() {});
            Map<String, ClientPathConfig> result = new LinkedHashMap<>();
            Object journeys = root.get("journeys");
            if (journeys instanceof List<?> list) {
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> rawJourney)) {
                        continue;
                    }
                    Map<String, Object> journey = (Map<String, Object>) rawJourney;
                    Optional<ClientPathConfig> config = parseSingle(journey);
                    if (config.isEmpty()) {
                        continue;
                    }
                    Object branches = journey.get("branches");
                    if (branches instanceof List<?> branchList) {
                        for (Object branchKey : branchList) {
                            if (branchKey != null && !branchKey.toString().isBlank()) {
                                result.put(branchKey.toString().trim(), config.get());
                            }
                        }
                    }
                    if (Boolean.TRUE.equals(journey.get("default"))) {
                        result.put("default", config.get());
                    }
                }
            } else {
                parseSingle(root).ifPresent(config -> result.put("default", config));
            }
            LOG.info("Loaded client path mappings: {}", result.keySet());
            return result;
        } catch (IOException e) {
            LOG.warn("Failed to load client path yaml: {}", path, e);
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Optional<ClientPathConfig> parseSingle(Map<String, Object> data) {
        String rootQuestionId = stringValue(data.get("root_question_id"));
        Object questionsRaw = data.get("questions");
        if (rootQuestionId.isBlank() || !(questionsRaw instanceof Map<?, ?> questionsMap)) {
            return Optional.empty();
        }

        Map<String, PathQuestion> questions = new HashMap<>();
        for (Map.Entry<?, ?> entry : questionsMap.entrySet()) {
            String questionId = String.valueOf(entry.getKey());
            if (!(entry.getValue() instanceof Map<?, ?> rawQuestion)) {
                continue;
            }
            Map<String, Object> question = (Map<String, Object>) rawQuestion;
            String text = stringValue(question.get("text"));
            PathInputType inputType = PathInputType.from(stringValue(question.get("input_type")));
            String inputKey = blankToNull(stringValue(question.get("input_key")));
            String script = blankToNull(stringValue(question.get("script")));
            String scriptId = blankToNull(stringValue(question.get("script_id")));
            String customElementId = blankToNull(stringValue(question.get("custom_element_id")));
            if (customElementId != null) {
                CustomPathElement element = customPathElementService.list().stream()
                        .filter(it -> customElementId.equals(it.id()))
                        .findFirst()
                        .orElse(null);
                if (element != null) {
                    scriptId = blankToNull(element.scriptId());
                }
            }
            Object optionsRaw = question.get("options");
            if (text.isBlank()) continue;
            List<PathOption> options = new ArrayList<>();
            List<?> rawOptions = optionsRaw instanceof List<?> ro ? ro : List.of();
            for (Object optionRaw : rawOptions) {
                if (!(optionRaw instanceof Map<?, ?> rawOption)) {
                    continue;
                }
                Map<String, Object> option = (Map<String, Object>) rawOption;
                String optionText = stringValue(option.get("text"));
                if (optionText.isBlank()) {
                    continue;
                }
                options.add(new PathOption(
                        optionText,
                        blankToNull(stringValue(option.get("next_question_id"))),
                        stringList(option.get("services")),
                        stringList(option.get("service_names")),
                        MultiServicesAction.from(stringValue(option.get("multi_services_action")))
                ));
            }
            if (inputType == PathInputType.OPTION && options.isEmpty()) continue;
            questions.put(questionId, new PathQuestion(
                    questionId,
                    text,
                    List.copyOf(options),
                    Boolean.TRUE.equals(question.get("include_other_services_option")),
                    inputType,
                    inputKey,
                    script,
                    scriptId
            ));
        }
        if (!questions.containsKey(rootQuestionId)) {
            return Optional.empty();
        }
        List<EntryAction> entryActions = parseEntryActions(data, questions, rootQuestionId);
        return Optional.of(new ClientPathConfig(rootQuestionId, Collections.unmodifiableMap(questions), entryActions));
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(item -> item != null && !item.toString().isBlank())
                .map(item -> item.toString().trim())
                .toList();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String normalizeServiceName(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private static List<EntryAction> parseEntryActions(Map<String, Object> data, Map<String, PathQuestion> questions, String fallbackRoot) {
        Object raw = data.get("entry_actions");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of(new EntryAction("take-ticket", "Взять талон", fallbackRoot));
        }
        List<EntryAction> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            String action = stringValue(map.get("action"));
            String label = stringValue(map.get("label"));
            String root = stringValue(map.get("root_question_id"));
            if (action.isBlank() || label.isBlank() || root.isBlank() || !questions.containsKey(root)) continue;
            out.add(new EntryAction(action, label, root));
        }
        return out.isEmpty() ? List.of(new EntryAction("take-ticket", "Взять талон", fallbackRoot)) : List.copyOf(out);
    }
}
