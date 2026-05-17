package ru.qsystems.telegrambot.api;

import io.micronaut.core.annotation.Nullable;
import io.swagger.v3.oas.annotations.media.Schema;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
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
import ru.qsystems.telegrambot.path.EntryAction;
import ru.qsystems.telegrambot.queue.QueueGateway;
import ru.qsystems.telegrambot.queue.ServiceFilter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Singleton
public class ChatCoreService {
    private final BranchConfigurationService branches;
    private final QueueGateway queueGateway;
    private final ServiceFilter serviceFilter;
    private final ClientPathService clientPathService;
    private final PathScriptExecutor pathScriptExecutor;
    private final ConcurrentMap<String, CoreSessionState> sessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> visitorToSession = new ConcurrentHashMap<>();

    public ChatCoreService(BranchConfigurationService branches, QueueGateway queueGateway, ServiceFilter serviceFilter, ClientPathService clientPathService, PathScriptExecutor pathScriptExecutor) {
        this.branches = branches;
        this.queueGateway = queueGateway;
        this.serviceFilter = serviceFilter;
        this.clientPathService = clientPathService;
        this.pathScriptExecutor = pathScriptExecutor;
    }

    public CoreResponse initialize(@Nullable InitRequest request) {
        CoreSessionState session = new CoreSessionState();
        session.sessionId = UUID.randomUUID().toString();
        session.visitorId = request != null && request.visitorId() != null && !request.visitorId().isBlank() ? request.visitorId() : UUID.randomUUID().toString();
        session.customerName = request != null && request.customerName() != null && !request.customerName().isBlank() ? request.customerName() : "web-client";
        sessions.put(session.sessionId, session);
        visitorToSession.put(session.visitorId, session.sessionId);

        if (branches.branchSelectionFirst() && branches.branches().size() > 1) {
            return enrich(new CoreResponse(session.sessionId, session.visitorId, "Сначала выберите отделение:", toBranchOptions(branches.branches()), false, null, null));
        }
        return enrich(new CoreResponse(session.sessionId, session.visitorId, "Выберите действие:", rootActions(session), false, null, null));
    }

    public CoreResponse act(String sessionId, CoreAction action) {
        CoreSessionState state = Optional.ofNullable(sessions.get(sessionId)).orElseThrow(() -> new IllegalArgumentException("Session not found"));
        if (action.customerName() != null && !action.customerName().isBlank()) {
            state.customerName = action.customerName();
        }
        if (action.type() == null || action.type().isBlank()) throw new IllegalArgumentException("Action type is required");
        if (action.type().startsWith("take-ticket")) {
            CoreResponse response = onTakeTicket(state, action.type());
            return enrich(new CoreResponse(response.sessionId(), state.visitorId, response.message(), response.options(), response.multiSelect(), response.ticket(), null));
        }
        CoreResponse response = switch (action.type()) {
            case "select-branch" -> onSelectBranch(state, action.value());
            case "path-option" -> onPathOption(state, action.value());
            case "path-other" -> onPathOther(state, action.value());
            case "path-input" -> onPathInput(state, action.value());
            case "select-service" -> onSelectService(state, action.value());
            default -> throw new IllegalArgumentException("Unsupported action type: " + action.type());
        };
        return enrich(new CoreResponse(response.sessionId(), state.visitorId, response.message(), response.options(), response.multiSelect(), response.ticket(), null));
    }

    private CoreResponse onTakeTicket(CoreSessionState state, String entryAction) { state.entryAction = entryAction; if (state.branchId != null && branches.byId(state.branchId).isPresent()) return startTicketFlow(state, branches.byId(state.branchId).orElseThrow()); Optional<String> single = branches.singleBranchId(); if (single.isPresent()) { state.branchId = single.get(); return startTicketFlow(state, branches.byId(single.get()).orElseThrow()); } return new CoreResponse(state.sessionId, state.visitorId, "Выберите отделение:", toBranchOptions(branches.branches()), false, null, null); }
    private CoreResponse onSelectBranch(CoreSessionState state, String branchId) { BranchConfig branch = branches.byId(branchId).orElseThrow(() -> new IllegalArgumentException("Branch not found")); state.branchId = branchId; if (branches.branchSelectionFirst()) return new CoreResponse(state.sessionId, state.visitorId, "Выберите действие:", rootActions(state), false, null, null); return startTicketFlow(state, branch); }
    private CoreResponse startTicketFlow(CoreSessionState state, BranchConfig branch) {
        List<ServiceInfo> services = loadVisibleServices(branch);
        Optional<ClientPathConfig> path = clientPathService.forBranch(branch);
        if (path.isPresent()) {
            PathQuestion root = resolveEntryRoot(path.get(), state.entryAction);
            state.pathAnswers.clear();
            state.scriptOutput.clear();
            state.pathAnswers.put("ticket_entry_action", state.entryAction);
            state.pathQuestionId = root.questionId();
            return toQuestionResponse(state, root, services);
        }
        state.pathMappedServiceIds.clear();
        state.selectedServiceIds.clear();
        state.pathAllowMultiChoice = null;
        return new CoreResponse(state.sessionId, state.visitorId, "Выберите услугу:", toServiceOptions(services), serviceFilter.multiServiceEnabled(branch), null, null);
    }
    private CoreResponse onPathOption(CoreSessionState state, String value) { BranchConfig branch = branchOf(state); ClientPathConfig path = clientPathService.forBranch(branch).orElseThrow(); String[] parts = value.split(":", 2); if (parts.length != 2) throw new IllegalArgumentException("Некорректный формат path-option"); PathQuestion q = path.questions().get(parts[0]); if (q == null) throw new IllegalArgumentException("Вопрос пути не найден"); int optionIndex; try { optionIndex = Integer.parseInt(parts[1]); } catch (NumberFormatException e) { throw new IllegalArgumentException("Некорректный индекс варианта", e); } if (optionIndex < 0 || optionIndex >= q.options().size()) throw new IllegalArgumentException("Индекс варианта вне диапазона"); PathOption option = q.options().get(optionIndex); state.pathAnswers.put(q.text(), option.text()); List<ServiceInfo> services = loadVisibleServices(branch); if (q.script() != null || q.scriptId() != null) { PathScriptResult scriptResult = pathScriptExecutor.execute(q.script(), q.scriptId(), Map.of("answer", option.text(), "answers", new HashMap<>(state.pathAnswers))); if (scriptResult == null) throw new IllegalArgumentException("Скрипт не вернул результат"); CoreResponse scriptResponse = applyScriptResult(state, branch, path, services, scriptResult); if (scriptResponse != null) return scriptResponse; }
        if (option.nextQuestionId() != null && !option.nextQuestionId().isBlank()) { PathQuestion next = path.questions().get(option.nextQuestionId()); if (next == null) throw new IllegalArgumentException("Следующий вопрос пути не найден"); state.pathQuestionId = next.questionId(); return toQuestionResponse(state, next, services);} List<String> serviceIds = clientPathService.optionServiceIds(option, services).stream().distinct().sorted().toList(); if (serviceIds.size() > 1 && option.multiServicesAction() == MultiServicesAction.AUTO) return finalizeVisit(state, branch, serviceIds); if (serviceIds.size() > 1) { state.pathMappedServiceIds = new HashSet<>(serviceIds); state.selectedServiceIds.clear(); state.pathAllowMultiChoice = option.multiServicesAction() == MultiServicesAction.CHOOSE_MANY; List<ServiceInfo> mapped = services.stream().filter(s -> serviceIds.contains(s.id())).toList(); return new CoreResponse(state.sessionId, state.visitorId, "По выбранному ответу доступны несколько услуг. Выберите нужную:", toServiceOptions(mapped), state.pathAllowMultiChoice, null, null);} return finalizeVisit(state, branch, serviceIds); }
	private CoreResponse onPathInput(CoreSessionState state, String value) { BranchConfig branch = branchOf(state); ClientPathConfig path = clientPathService.forBranch(branch).orElseThrow(); PathQuestion q = path.questions().get(state.pathQuestionId); if (q == null) throw new IllegalArgumentException("Вопрос пути не найден"); if (value == null || value.isBlank()) throw new IllegalArgumentException("Значение path-input не должно быть пустым"); String key = q.inputKey() != null ? q.inputKey() : q.questionId(); state.pathAnswers.put(key, value); state.pathAnswers.put(q.text(), value); List<ServiceInfo> services = loadVisibleServices(branch); PathScriptResult result = pathScriptExecutor.execute(q.script(), q.scriptId(), Map.of("answer", value, "answers", new HashMap<>(state.pathAnswers))); if (result == null) throw new IllegalArgumentException("Скрипт не вернул результат"); CoreResponse scriptResponse = applyScriptResult(state, branch, path, services, result); if (scriptResponse != null) return scriptResponse; throw new IllegalArgumentException("Скрипт не вернул услуги"); }
    private CoreResponse applyScriptResult(CoreSessionState state, BranchConfig branch, ClientPathConfig path, List<ServiceInfo> services, PathScriptResult result) {
        state.scriptOutput.putAll(result.visitParameters());
        if (result.nextQuestionId() != null && !result.nextQuestionId().isBlank()) { PathQuestion next = path.questions().get(result.nextQuestionId()); if (next == null) throw new IllegalArgumentException("Следующий вопрос пути не найден"); state.pathQuestionId = next.questionId(); return toQuestionResponse(state, next, services); }
        List<String> serviceIds = new ArrayList<>(result.serviceIds());
        if (serviceIds.isEmpty() && !result.serviceNames().isEmpty()) { Set<String> names = result.serviceNames().stream().map(String::toLowerCase).collect(java.util.stream.Collectors.toSet()); serviceIds = services.stream().filter(s -> names.contains(s.name().toLowerCase())).map(ServiceInfo::id).toList(); }
        if (serviceIds.isEmpty()) return null;
        if (serviceIds.size() > 1 && result.multiServicesAction() != MultiServicesAction.AUTO) {
            state.pathMappedServiceIds = new HashSet<>(serviceIds);
            state.selectedServiceIds.clear();
            state.pathAllowMultiChoice = result.multiServicesAction() == MultiServicesAction.CHOOSE_MANY;
            final List<String> resolvedServiceIds = serviceIds;
            List<ServiceInfo> mapped = services.stream().filter(s -> resolvedServiceIds.contains(s.id())).toList();
            return new CoreResponse(state.sessionId, state.visitorId, "По скрипту доступны несколько услуг. Выберите нужную:", toServiceOptions(mapped), state.pathAllowMultiChoice, null, null);
        }
        return finalizeVisit(state, branch, serviceIds);
    }

    private CoreResponse onPathOther(CoreSessionState state, String questionId) { BranchConfig branch = branchOf(state); ClientPathConfig path = clientPathService.forBranch(branch).orElseThrow(); PathQuestion question = path.questions().get(questionId); if (question == null) throw new IllegalArgumentException("Вопрос пути не найден"); List<ServiceInfo> services = loadVisibleServices(branch); Set<String> used = new HashSet<>(); for (PathOption option : question.options()) used.addAll(clientPathService.optionServiceIds(option, services)); List<ServiceInfo> other = services.stream().filter(s -> !used.contains(s.id())).sorted(Comparator.comparing(ServiceInfo::name)).toList(); state.pathMappedServiceIds = other.stream().map(ServiceInfo::id).collect(java.util.stream.Collectors.toSet()); state.selectedServiceIds.clear(); state.pathAllowMultiChoice = false; return new CoreResponse(state.sessionId, state.visitorId, "Выберите услугу:", toServiceOptions(other), false, null, null); }
    private CoreResponse onSelectService(CoreSessionState state, String serviceId) { BranchConfig branch = branchOf(state); List<ServiceInfo> services = mappedServices(state, loadVisibleServices(branch)); boolean multi = state.pathAllowMultiChoice != null ? state.pathAllowMultiChoice : serviceFilter.multiServiceEnabled(branch); Set<String> allowedServiceIds = services.stream().map(ServiceInfo::id).collect(java.util.stream.Collectors.toSet()); if (multi && !"confirm".equals(serviceId)) { if (!allowedServiceIds.contains(serviceId)) throw new IllegalArgumentException("Услуга недоступна для выбора"); if (state.selectedServiceIds.contains(serviceId)) state.selectedServiceIds.remove(serviceId); else state.selectedServiceIds.add(serviceId); return new CoreResponse(state.sessionId, state.visitorId, "Выберите услуги и нажмите confirm", toServiceOptions(services), true, null, null);} List<String> selected = "confirm".equals(serviceId) ? state.selectedServiceIds.stream().sorted().toList() : List.of(serviceId); if (selected.isEmpty()) throw new IllegalArgumentException("Не выбраны услуги для подтверждения"); for (String selectedId : selected) { if (!allowedServiceIds.contains(selectedId)) throw new IllegalArgumentException("Услуга недоступна для выбора"); } return finalizeVisit(state, branch, selected); }
    private CoreResponse finalizeVisit(CoreSessionState state, BranchConfig branch, List<String> serviceIds) { Map<String, String> answers = new HashMap<>(state.pathAnswers); answers.putAll(state.scriptOutput); answers.put("WebSessionId", state.sessionId); answers.put("WebVisitorId", state.visitorId); Map<String, Object> visit = queueGateway.createVisit(branch, serviceIds, state.visitorId, state.customerName, answers).orElseThrow(); Object ticket = visit.getOrDefault("ticketId", visit.get("ticket")); return new CoreResponse(state.sessionId, state.visitorId , "Ваш талон: " + (ticket == null ? "создан" : ticket), List.of(), false, String.valueOf(ticket), null); }

    public Optional<String> sessionForVisitor(String visitorId) { return Optional.ofNullable(visitorToSession.get(visitorId)); }
    public boolean sessionExists(String sessionId) { return sessions.containsKey(sessionId); }
    private CoreResponse enrich(CoreResponse response) { return new CoreResponse(response.sessionId(), response.visitorId(), response.message(), response.options(), response.multiSelect(), response.ticket(), "/ws/events/" + response.sessionId()); }
    private List<ServiceInfo> loadVisibleServices(BranchConfig branch) { return serviceFilter.visibleServices(queueGateway.getServices(branch)); }
    private BranchConfig branchOf(CoreSessionState state) {
        if (state.branchId == null || state.branchId.isBlank()) {
            throw new IllegalArgumentException("Сначала выберите отделение действием select-branch");
        }
        return branches.byId(state.branchId).orElseThrow(() -> new IllegalArgumentException("Branch not found: " + state.branchId));
    }
    private List<ServiceInfo> mappedServices(CoreSessionState state, List<ServiceInfo> services) { return state.pathMappedServiceIds.isEmpty() ? services : services.stream().filter(s -> state.pathMappedServiceIds.contains(s.id())).toList(); }
    private static List<CoreOption> toBranchOptions(List<BranchConfig> branches) { return branches.stream().map(b -> new CoreOption("select-branch:" + b.branchId(), b.name())).toList(); }
    private static List<CoreOption> toPathOptions(PathQuestion q) { List<CoreOption> out = new ArrayList<>(); for (int i = 0; i < q.options().size(); i++) out.add(new CoreOption("path-option:" + q.questionId() + ":" + i, q.options().get(i).text())); if (q.includeOtherServicesOption()) out.add(new CoreOption("path-other:" + q.questionId(), "Другое")); return out; }
    private CoreResponse toQuestionResponse(CoreSessionState state, PathQuestion q, List<ServiceInfo> services) { if (q.inputType() == ru.qsystems.telegrambot.path.PathInputType.OPTION) return new CoreResponse(state.sessionId, state.visitorId, q.text(), toPathOptions(q), false, null, null); return new CoreResponse(state.sessionId, state.visitorId, q.text(), List.of(new CoreOption("path-input", "Отправить значение")), false, null, null); }
    private static List<CoreOption> toServiceOptions(List<ServiceInfo> services) { List<CoreOption> options = new ArrayList<>(services.stream().map(s -> new CoreOption("select-service:" + s.id(), s.name())).toList()); options.add(new CoreOption("select-service:confirm", "Подтвердить выбор")); return options; }


    private List<CoreOption> rootActions(CoreSessionState state) {
        BranchConfig selected = state.branchId == null ? null : branches.byId(state.branchId).orElse(null);
        if (selected != null) {
            Optional<ClientPathConfig> cfg = clientPathService.forBranch(selected);
            if (cfg.isPresent()) {
                return cfg.get().entryActions().stream().map(a -> new CoreOption(a.action(), a.label())).toList();
            }
        }
        return List.of(new CoreOption("take-ticket", "Взять талон"));
    }

    private static PathQuestion resolveEntryRoot(ClientPathConfig config, String action) {
        for (EntryAction entryAction : config.entryActions()) {
            if (entryAction.action().equals(action)) {
                PathQuestion q = config.questions().get(entryAction.rootQuestionId());
                if (q != null) return q;
            }
        }
        PathQuestion fallback = config.rootQuestion();
        if (fallback == null) throw new IllegalArgumentException("Root question not found");
        return fallback;
    }

    @Serdeable
    @Schema(name = "ChatInitRequest", description = "Запрос инициализации сессии чата. Позволяет передать человеко-читаемые данные пользователя (например, ФИО).")
    public record InitRequest(
            @Schema(description = "Стабильный внешний ID посетителя (если фронт его уже знает)", example = "crm-user-1024") String visitorId,
            @Schema(description = "Человеко-читаемое имя посетителя (ФИО)", example = "Иванов Иван Иванович") String customerName
    ) {}
    @Serdeable
    public record CoreAction(String type, String value, String customerName) {}
    @Serdeable
    public record CoreOption(String action, String label) {}
    @Serdeable
    public record CoreResponse(String sessionId, String visitorId, String message, List<CoreOption> options, boolean multiSelect, String ticket, String wsEndpoint) {}
    private static final class CoreSessionState { private String sessionId; private String visitorId; private String branchId; private String customerName = "web-client"; private String pathQuestionId; private final Map<String, String> pathAnswers = new HashMap<>(); private final Map<String, String> scriptOutput = new HashMap<>(); private Set<String> pathMappedServiceIds = new HashSet<>(); private final Set<String> selectedServiceIds = new HashSet<>(); private Boolean pathAllowMultiChoice; private String entryAction = "take-ticket"; }
}
