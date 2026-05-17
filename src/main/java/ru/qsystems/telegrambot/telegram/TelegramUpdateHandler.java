package ru.qsystems.telegrambot.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.qsystems.telegrambot.config.BranchConfigurationService;
import ru.qsystems.telegrambot.model.BranchConfig;
import ru.qsystems.telegrambot.model.ServiceInfo;
import ru.qsystems.telegrambot.path.ClientPathConfig;
import ru.qsystems.telegrambot.path.ClientPathService;
import ru.qsystems.telegrambot.path.MultiServicesAction;
import ru.qsystems.telegrambot.path.PathOption;
import ru.qsystems.telegrambot.path.PathQuestion;
import ru.qsystems.telegrambot.path.PathScriptExecutor;
import ru.qsystems.telegrambot.path.PathScriptResult;
import ru.qsystems.telegrambot.queue.QueueGateway;
import ru.qsystems.telegrambot.queue.ServiceFilter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Singleton
public class TelegramUpdateHandler {
    private static final Logger LOG = LoggerFactory.getLogger(TelegramUpdateHandler.class);

    private final TelegramApiClient telegram;
    private final BranchConfigurationService branches;
    private final QueueGateway queueGateway;
    private final ServiceFilter serviceFilter;
    private final KeyboardFactory keyboardFactory;
    private final UserStateStore stateStore;
    private final ClientPathService clientPathService;
    private final PathScriptExecutor pathScriptExecutor;
    private final UserRateLimiter userRateLimiter;

    public TelegramUpdateHandler(
            TelegramApiClient telegram,
            BranchConfigurationService branches,
            QueueGateway queueGateway,
            ServiceFilter serviceFilter,
            KeyboardFactory keyboardFactory,
            UserStateStore stateStore,
            ClientPathService clientPathService,
            PathScriptExecutor pathScriptExecutor,
            UserRateLimiter userRateLimiter
    ) {
        this.telegram = telegram;
        this.branches = branches;
        this.queueGateway = queueGateway;
        this.serviceFilter = serviceFilter;
        this.keyboardFactory = keyboardFactory;
        this.stateStore = stateStore;
        this.clientPathService = clientPathService;
        this.pathScriptExecutor = pathScriptExecutor;
        this.userRateLimiter = userRateLimiter;
    }

    public void handle(JsonNode update) {
        if (update.hasNonNull("message")) {
            handleMessage(update.get("message"));
        } else if (update.hasNonNull("callback_query")) {
            handleCallback(update.get("callback_query"));
        }
    }

    private void handleMessage(JsonNode message) {
        long chatId = message.path("chat").path("id").asLong();
        long userId = message.path("from").path("id").asLong(chatId);
        if (!userRateLimiter.allow(userId)) {
            telegram.sendMessage(chatId, "Слишком много запросов. Пожалуйста, подождите несколько секунд.", null);
            return;
        }
        String text = message.path("text").asText("").trim();
        UserState state = stateStore.get(userId);
        if (state.getStateName() == StateName.GET_TICKET && !text.isBlank() && onPathInputMessage(chatId, userId, text, state, fullName(message.path("from")))) return;
        if ("/reset".equals(text)) {
            stateStore.reset(userId);
            telegram.sendMessage(chatId, "Сессия сброшена. Используйте /start для нового диалога.", null);
            return;
        }
        if ("/start".equals(text) || "/help".equals(text)) {
            state.clearConversation();
            telegram.sendMessage(chatId, "Добро пожаловать!", null);
            if (branches.branchSelectionFirst() && branches.branches().size() > 1) {
                telegram.sendMessage(chatId, "Сначала выберите отделение:", keyboardFactory.chooseBranchButton());
            } else {
                telegram.sendMessage(chatId, "Выберите действие:", keyboardFactory.mainMenu());
            }
        }
    }


    @SuppressWarnings("unchecked")
    private boolean onPathInputMessage(long chatId, long userId, String text, UserState state, String fullName) {
        BranchConfig branch = currentBranch(state).orElse(null);
        if (branch == null) return false;
        ClientPathConfig path = clientPathService.forBranch(branch).orElse(null);
        if (path == null) return false;
        String qid = String.valueOf(state.data().getOrDefault("path_question_id", ""));
        PathQuestion q = path.questions().get(qid);
        if (q == null || q.inputType() != ru.qsystems.telegrambot.path.PathInputType.TEXT) return false;
        Map<String, String> answers = (Map<String, String>) state.data().computeIfAbsent("path_answers", i -> new HashMap<String, String>());
        String key = q.inputKey() == null || q.inputKey().isBlank() ? q.questionId() : q.inputKey();
        answers.put(key, text);
        answers.put(q.text(), text);
        List<ServiceInfo> services = loadVisibleServices(branch);
        PathScriptResult result = pathScriptExecutor.execute(q.script(), q.scriptId(), Map.of("answer", text, "answers", new HashMap<>(answers)));
        if (result == null) { telegram.sendMessage(chatId, "Скрипт шага не вернул результат.", null); return true; }
        if (result.message() != null && !result.message().isBlank()) telegram.sendMessage(chatId, result.message(), null);
        if (result.nextQuestionId() != null && !result.nextQuestionId().isBlank()) {
            PathQuestion next = path.questions().get(result.nextQuestionId());
            if (next == null) { telegram.sendMessage(chatId, "Следующий вопрос не найден.", null); return true; }
            state.data().put("path_question_id", next.questionId());
            telegram.sendMessage(chatId, next.text(), next.inputType() == ru.qsystems.telegrambot.path.PathInputType.OPTION ? keyboardFactory.clientPath(next, services) : null);
            return true;
        }
        List<String> serviceIds = new ArrayList<>(result.serviceIds());
        if (serviceIds.isEmpty() && !result.serviceNames().isEmpty()) {
            Set<String> names = result.serviceNames().stream().map(String::toLowerCase).collect(java.util.stream.Collectors.toSet());
            serviceIds = services.stream().filter(sv -> names.contains(sv.name().toLowerCase())).map(ServiceInfo::id).toList();
        }
        if (serviceIds.isEmpty()) { telegram.sendMessage(chatId, "Скрипт не вернул услуги.", null); return true; }
        Map<String, String> params = new HashMap<>(answers); params.putAll(result.visitParameters());
        queueGateway.createVisit(branch, serviceIds, String.valueOf(userId), fullName, params).ifPresentOrElse(visit -> {
            Object ticket = visit.getOrDefault("ticketId", visit.get("ticket"));
            telegram.sendMessage(chatId, "Ваш талон: " + (ticket == null ? "создан" : ticket), null);
            stateStore.addBranchSubscription(userId, branch.prefix());
            state.clearConversation();
        }, () -> telegram.sendMessage(chatId, "Ошибка создания талона", null));
        return true;
    }
    private void handleCallback(JsonNode callback) {
        String callbackId = callback.path("id").asText(null);
        telegram.answerCallbackQuery(callbackId);

        String data = callback.path("data").asText("");
        long userId = callback.path("from").path("id").asLong();
        String fullName = fullName(callback.path("from"));
        long chatId = callback.path("message").path("chat").path("id").asLong(userId);
        if (!userRateLimiter.allow(userId)) {
            telegram.sendMessage(chatId, "Слишком много запросов. Пожалуйста, подождите несколько секунд.", null);
            return;
        }
        long messageId = callback.path("message").path("message_id").asLong(0L);

        UserState state = stateStore.get(userId);
        try {
            if ("take-ticket".equals(data)) {
                onTakeTicket(chatId, userId, state);
            } else if ("choose-branch".equals(data)) {
                telegram.sendMessage(chatId, "Выберите отделение:", keyboardFactory.branches(branches.branches()));
                state.setStateName(StateName.BRANCH);
            } else if (data.startsWith("branch:")) {
                onBranchSelected(chatId, data.substring("branch:".length()), state);
            } else if (data.startsWith("path:")) {
                onPathOption(chatId, userId, fullName, state, data);
            } else if (data.startsWith("path_other:")) {
                onPathOther(chatId, state, data.substring("path_other:".length()));
            } else if (data.startsWith("service:")) {
                onServiceSelected(chatId, messageId, userId, fullName, state, data.substring("service:".length()));
            }
        } catch (Exception e) {
            LOG.error("Telegram callback processing failed. data={}", data, e);
            telegram.sendMessage(chatId, "Ошибка обработки запроса. Попробуйте начать заново командой /start", null);
        }
    }

    private void onTakeTicket(long chatId, long userId, UserState state) {
        Optional<String> presetBranchId = optionalString(state.data().get("branch_id"));
        if (presetBranchId.isPresent() && branches.byId(presetBranchId.get()).isPresent()) {
            startTicketFlow(chatId, state, branches.byId(presetBranchId.get()).orElseThrow());
            return;
        }
        Optional<String> singleBranchId = branches.singleBranchId();
        if (singleBranchId.isPresent()) {
            BranchConfig branch = branches.byId(singleBranchId.get()).orElseThrow();
            state.data().put("branch_id", branch.branchId());
            startTicketFlow(chatId, state, branch);
            return;
        }
        telegram.sendMessage(chatId, "Выберите отделение:", keyboardFactory.branches(branches.branches()));
        state.setStateName(StateName.BRANCH);
    }

    private void onBranchSelected(long chatId, String branchId, UserState state) {
        Optional<BranchConfig> branch = branches.byId(branchId);
        if (branch.isEmpty()) {
            telegram.sendMessage(chatId, "Не удалось выбрать отделение", null);
            state.clearConversation();
            return;
        }
        state.data().put("branch_id", branchId);
        if (branches.branchSelectionFirst()) {
            telegram.sendMessage(chatId, "Выберите действие:", keyboardFactory.mainMenu());
            state.setStateName(StateName.APPOINTMENT);
        } else {
            startTicketFlow(chatId, state, branch.get());
        }
    }

    private void startTicketFlow(long chatId, UserState state, BranchConfig branch) {
        List<ServiceInfo> services = loadVisibleServices(branch);
        Optional<ClientPathConfig> path = clientPathService.forBranch(branch);
        if (path.isPresent()) {
            PathQuestion root = path.get().rootQuestion();
            state.data().put("path_question_id", root.questionId());
            state.data().put("path_answers", new HashMap<String, String>());
            telegram.sendMessage(chatId, root.text(), root.inputType() == ru.qsystems.telegrambot.path.PathInputType.OPTION ? keyboardFactory.clientPath(root, services) : null);
        } else {
            state.data().put("path_mapped_service_ids", List.of());
            state.data().put("selected_service_ids", new HashSet<String>());
            state.data().put("path_allow_multi_choice", null);
            telegram.sendMessage(
                    chatId,
                    "Выберите услугу:",
                    keyboardFactory.services(services, Set.of(), serviceFilter.multiServiceEnabled(branch))
            );
        }
        state.setStateName(StateName.GET_TICKET);
    }

    @SuppressWarnings("unchecked")
    private void onPathOption(long chatId, long userId, String fullName, UserState state, String data) {
        String[] parts = data.split(":", 3);
        if (parts.length != 3) {
            telegram.sendMessage(chatId, "Некорректный вариант ответа", null);
            return;
        }
        BranchConfig branch = currentBranch(state).orElse(null);
        if (branch == null) {
            telegram.sendMessage(chatId, "Отделение не найдено", null);
            state.clearConversation();
            return;
        }
        ClientPathConfig path = clientPathService.forBranch(branch).orElse(null);
        if (path == null) {
            telegram.sendMessage(chatId, "Сценарий клиента для отделения не настроен", null);
            return;
        }
        PathQuestion question = path.questions().get(parts[1]);
        if (question == null) {
            telegram.sendMessage(chatId, "Маршрут клиента устарел, начните заново", null);
            state.clearConversation();
            return;
        }
        int optionIndex;
        try {
            optionIndex = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            telegram.sendMessage(chatId, "Некорректный вариант ответа", null);
            return;
        }
        if (optionIndex < 0 || optionIndex >= question.options().size()) {
            telegram.sendMessage(chatId, "Некорректный вариант ответа", null);
            return;
        }

        PathOption option = question.options().get(optionIndex);
        Map<String, String> pathAnswers = (Map<String, String>) state.data().computeIfAbsent("path_answers", ignored -> new HashMap<String, String>());
        pathAnswers.put(question.text(), option.text());
        List<ServiceInfo> services = loadVisibleServices(branch);

        if (option.nextQuestionId() != null && !option.nextQuestionId().isBlank()) {
            PathQuestion next = path.questions().get(option.nextQuestionId());
            if (next == null) {
                telegram.sendMessage(chatId, "Маршрут клиента настроен неверно", null);
                state.clearConversation();
                return;
            }
            state.data().put("path_question_id", next.questionId());
            telegram.sendMessage(chatId, next.text(), next.inputType() == ru.qsystems.telegrambot.path.PathInputType.OPTION ? keyboardFactory.clientPath(next, services) : null);
            state.setStateName(StateName.GET_TICKET);
            return;
        }

        List<String> serviceIds = clientPathService.optionServiceIds(option, services).stream().distinct().sorted().toList();
        if (serviceIds.isEmpty()) {
            telegram.sendMessage(chatId, "Для этого варианта не настроены услуги", null);
            return;
        }

        if (serviceIds.size() > 1 && option.multiServicesAction() == MultiServicesAction.AUTO) {
            createVisitAndFinish(chatId, userId, fullName, state, branch, serviceIds, pathAnswers);
            return;
        }

        if (serviceIds.size() > 1) {
            List<ServiceInfo> mappedServices = services.stream()
                    .filter(service -> serviceIds.contains(service.id()))
                    .toList();
            boolean allowMultiChoice = option.multiServicesAction() == MultiServicesAction.CHOOSE_MANY;
            state.data().put("path_mapped_service_ids", serviceIds);
            state.data().put("selected_service_ids", new HashSet<String>());
            state.data().put("path_allow_multi_choice", allowMultiChoice);
            telegram.sendMessage(
                    chatId,
                    "По выбранному ответу доступны несколько услуг. Выберите нужную:",
                    keyboardFactory.services(mappedServices, Set.of(), allowMultiChoice)
            );
            state.setStateName(StateName.GET_TICKET);
            return;
        }

        createVisitAndFinish(chatId, userId, fullName, state, branch, serviceIds, pathAnswers);
    }

    private void onPathOther(long chatId, UserState state, String questionId) {
        BranchConfig branch = currentBranch(state).orElse(null);
        if (branch == null) {
            return;
        }
        ClientPathConfig path = clientPathService.forBranch(branch).orElse(null);
        if (path == null) {
            return;
        }
        PathQuestion question = path.questions().get(questionId);
        if (question == null) {
            telegram.sendMessage(chatId, "Маршрут клиента устарел, начните заново", null);
            state.clearConversation();
            return;
        }
        List<ServiceInfo> services = loadVisibleServices(branch);
        Set<String> used = new HashSet<>();
        for (PathOption option : question.options()) {
            used.addAll(clientPathService.optionServiceIds(option, services));
        }
        List<ServiceInfo> other = services.stream()
                .filter(service -> !used.contains(service.id()))
                .sorted(Comparator.comparing(ServiceInfo::name))
                .toList();
        if (other.isEmpty()) {
            telegram.sendMessage(chatId, "Других услуг не найдено", null);
            return;
        }
        state.data().put("path_mapped_service_ids", other.stream().map(ServiceInfo::id).toList());
        state.data().put("selected_service_ids", new HashSet<String>());
        state.data().put("path_allow_multi_choice", false);
        telegram.sendMessage(chatId, "Выберите услугу:", keyboardFactory.services(other, Set.of(), false));
        state.setStateName(StateName.GET_TICKET);
    }

    @SuppressWarnings("unchecked")
    private void onServiceSelected(long chatId, long messageId, long userId, String fullName, UserState state, String serviceData) {
        BranchConfig branch = currentBranch(state).orElse(null);
        if (branch == null) {
            telegram.sendMessage(chatId, "Отделение не найдено", null);
            state.clearConversation();
            return;
        }
        List<ServiceInfo> services = mappedServices(state, loadVisibleServices(branch));
        Set<String> availableIds = services.stream().map(ServiceInfo::id).collect(java.util.stream.Collectors.toSet());
        Boolean pathAllowMulti = (Boolean) state.data().get("path_allow_multi_choice");
        boolean multiEnabled = pathAllowMulti != null ? pathAllowMulti : serviceFilter.multiServiceEnabled(branch);
        Set<String> selectedIds = (Set<String>) state.data().computeIfAbsent("selected_service_ids", ignored -> new HashSet<String>());

        List<String> serviceIds;
        if ("confirm".equals(serviceData)) {
            if (selectedIds.isEmpty()) {
                telegram.sendMessage(chatId, "Выберите хотя бы одну услугу", null);
                return;
            }
            serviceIds = selectedIds.stream().sorted().toList();
        } else {
            String serviceId = serviceData;
            if (!availableIds.contains(serviceId)) {
                telegram.sendMessage(chatId, "Эта услуга недоступна для текущего шага. Выберите из предложенных вариантов.", null);
                return;
            }
            if (multiEnabled) {
                if (selectedIds.contains(serviceId)) {
                    selectedIds.remove(serviceId);
                } else {
                    selectedIds.add(serviceId);
                }
                telegram.editMessageReplyMarkup(chatId, messageId, keyboardFactory.services(services, selectedIds, true));
                return;
            }
            serviceIds = List.of(serviceId);
        }

        Map<String, String> pathAnswers = castPathAnswers(state.data().get("path_answers"));
        createVisitAndFinish(chatId, userId, fullName, state, branch, serviceIds, pathAnswers);
    }

    private void createVisitAndFinish(
            long chatId,
            long userId,
            String fullName,
            UserState state,
            BranchConfig branch,
            List<String> serviceIds,
            Map<String, String> pathAnswers
    ) {
        queueGateway.createVisit(branch, serviceIds, String.valueOf(userId), fullName, pathAnswers)
                .ifPresentOrElse(visit -> {
                    Object ticket = visit.getOrDefault("ticketId", visit.get("ticket"));
                    telegram.sendMessage(chatId, "Ваш талон: " + (ticket == null ? "создан" : ticket), null);
                    stateStore.addBranchSubscription(userId, branch.prefix());
                    state.clearConversation();
                }, () -> telegram.sendMessage(chatId, "Ошибка создания талона", null));
    }

    private List<ServiceInfo> loadVisibleServices(BranchConfig branch) {
        return serviceFilter.visibleServices(queueGateway.getServices(branch));
    }

    private List<ServiceInfo> mappedServices(UserState state, List<ServiceInfo> allServices) {
        Set<String> mappedIds = new HashSet<>();
        Object raw = state.data().get("path_mapped_service_ids");
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                mappedIds.add(String.valueOf(item));
            }
        }
        if (mappedIds.isEmpty()) {
            return allServices;
        }
        List<ServiceInfo> mapped = allServices.stream()
                .filter(service -> mappedIds.contains(service.id()))
                .toList();
        return mapped.isEmpty() ? allServices : mapped;
    }

    private Optional<BranchConfig> currentBranch(UserState state) {
        return optionalString(state.data().get("branch_id")).flatMap(branches::byId);
    }

    private static Optional<String> optionalString(Object value) {
        return value == null || value.toString().isBlank() ? Optional.empty() : Optional.of(value.toString());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> castPathAnswers(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, String> result = new HashMap<>();
            map.forEach((key, value) -> {
                if (key != null && value != null) {
                    result.put(String.valueOf(key), String.valueOf(value));
                }
            });
            return result;
        }
        return Map.of();
    }

    private static String fullName(JsonNode from) {
        List<String> parts = new ArrayList<>();
        if (from.hasNonNull("first_name")) {
            parts.add(from.get("first_name").asText());
        }
        if (from.hasNonNull("last_name")) {
            parts.add(from.get("last_name").asText());
        }
        String result = String.join(" ", parts).trim();
        if (result.isBlank() && from.hasNonNull("username")) {
            result = from.get("username").asText();
        }
        return result.isBlank() ? String.valueOf(from.path("id").asLong()) : result;
    }
}
