package ru.qsystems.telegrambot.telegram;

import jakarta.inject.Singleton;
import ru.qsystems.telegrambot.model.BranchConfig;
import ru.qsystems.telegrambot.model.ServiceInfo;
import ru.qsystems.telegrambot.path.ClientPathService;
import ru.qsystems.telegrambot.path.PathOption;
import ru.qsystems.telegrambot.path.PathQuestion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Singleton
public class KeyboardFactory {
    private final ClientPathService clientPathService;

    public KeyboardFactory(ClientPathService clientPathService) {
        this.clientPathService = clientPathService;
    }

    public Map<String, Object> mainMenu() {
        return inline(List.of(List.of(button("Взять талон", "take-ticket"))));
    }

    public Map<String, Object> chooseBranchButton() {
        return inline(List.of(List.of(button("Выбрать отделение", "choose-branch"))));
    }

    public Map<String, Object> branches(List<BranchConfig> branches) {
        List<List<Map<String, String>>> rows = new ArrayList<>();
        for (BranchConfig branch : branches) {
            rows.add(List.of(button(branch.name(), "branch:" + branch.branchId())));
        }
        return inline(rows);
    }

    public Map<String, Object> services(List<ServiceInfo> services, Set<String> selectedIds, boolean multiEnabled) {
        List<List<Map<String, String>>> rows = new ArrayList<>();
        List<Map<String, String>> current = new ArrayList<>(2);
        for (ServiceInfo service : services) {
            String prefix = selectedIds.contains(service.id()) ? "✅ " : "";
            current.add(button(prefix + service.name(), "service:" + service.id()));
            if (current.size() == 2) {
                rows.add(current);
                current = new ArrayList<>(2);
            }
        }
        if (!current.isEmpty()) {
            rows.add(current);
        }
        if (multiEnabled) {
            rows.add(List.of(button("Подтвердить выбор", "service:confirm")));
        }
        return inline(rows);
    }

    public Map<String, Object> clientPath(PathQuestion question, List<ServiceInfo> services) {
        List<List<Map<String, String>>> rows = new ArrayList<>();
        for (int i = 0; i < question.options().size(); i++) {
            PathOption option = question.options().get(i);
            rows.add(List.of(button(option.text(), "path:" + question.questionId() + ":" + i)));
        }
        if (question.includeOtherServicesOption()) {
            Set<String> used = question.options().stream()
                    .flatMap(option -> clientPathService.optionServiceIds(option, services).stream())
                    .collect(java.util.stream.Collectors.toSet());
            boolean hasOther = services.stream().anyMatch(service -> !used.contains(service.id()));
            if (hasOther) {
                rows.add(List.of(button("Другое", "path_other:" + question.questionId())));
            }
        }
        return inline(rows);
    }

    private static Map<String, Object> inline(List<List<Map<String, String>>> rows) {
        Map<String, Object> keyboard = new LinkedHashMap<>();
        keyboard.put("inline_keyboard", rows);
        return keyboard;
    }

    private static Map<String, String> button(String text, String callbackData) {
        Map<String, String> button = new LinkedHashMap<>();
        button.put("text", text);
        button.put("callback_data", callbackData);
        return button;
    }
}
