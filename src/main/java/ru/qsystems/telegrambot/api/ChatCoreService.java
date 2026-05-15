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
    private final ConcurrentMap<String, CoreSessionState> sessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> visitorToSession = new ConcurrentHashMap<>();

    public ChatCoreService(BranchConfigurationService branches, QueueGateway queueGateway, ServiceFilter serviceFilter, ClientPathService clientPathService) {
        this.branches = branches;
        this.queueGateway = queueGateway;
        this.serviceFilter = serviceFilter;
        this.clientPathService = clientPathService;
    }

    public CoreResponse initialize(@Nullable InitRequest request) {
        CoreSessionState session = new CoreSessionState();
        session.sessionId = UUID.randomUUID().toString();
        session.visitorId = request != null && request.visitorId() != null && !request.visitorId().isBlank() ? request.visitorId() : UUID.randomUUID().toString();
        session.customerName = request != null && request.customerName() != null && !request.customerName().isBlank() ? request.customerName() : "web-client";
        sessions.put(session.sessionId, session);
        visitorToSession.put(session.visitorId, session.sessionId);

        if (branches.branchSelectionFirst() && branches.branches().size() > 1) {
            return new CoreResponse(session.sessionId, session.visitorId, "Сначала выберите отделение:", toBranchOptions(branches.branches()), false, null);
        }
        return new CoreResponse(session.sessionId, session.visitorId, "Выберите действие:", List.of(new CoreOption("take-ticket", "Получить талон")), false, null);
    }

    public CoreResponse act(String sessionId, CoreAction action) {
        CoreSessionState state = Optional.ofNullable(sessions.get(sessionId)).orElseThrow(() -> new IllegalArgumentException("Session not found"));
        if (action.customerName() != null && !action.customerName().isBlank()) {
            state.customerName = action.customerName();
        }
        CoreResponse response = switch (action.type()) {
            case "take-ticket" -> onTakeTicket(state);
            case "select-branch" -> onSelectBranch(state, action.value());
            case "path-option" -> onPathOption(state, action.value());
            case "path-other" -> onPathOther(state, action.value());
            case "select-service" -> onSelectService(state, action.value());
            default -> throw new IllegalArgumentException("Unsupported action type: " + action.type());
        };
        return new CoreResponse(response.sessionId(), state.visitorId, response.message(), response.options(), response.multiSelect(), response.ticket());
    }

    private CoreResponse onTakeTicket(CoreSessionState state) { if (state.branchId != null && branches.byId(state.branchId).isPresent()) return startTicketFlow(state, branches.byId(state.branchId).orElseThrow()); Optional<String> single = branches.singleBranchId(); if (single.isPresent()) { state.branchId = single.get(); return startTicketFlow(state, branches.byId(single.get()).orElseThrow()); } return new CoreResponse(state.sessionId, state.visitorId, "Выберите отделение:", toBranchOptions(branches.branches()), false, null); }
    private CoreResponse onSelectBranch(CoreSessionState state, String branchId) { BranchConfig branch = branches.byId(branchId).orElseThrow(() -> new IllegalArgumentException("Branch not found")); state.branchId = branchId; if (branches.branchSelectionFirst()) return new CoreResponse(state.sessionId, state.visitorId, "Выберите действие:", List.of(new CoreOption("take-ticket", "Получить талон")), false, null); return startTicketFlow(state, branch); }
    private CoreResponse startTicketFlow(CoreSessionState state, BranchConfig branch) { List<ServiceInfo> services = loadVisibleServices(branch); Optional<ClientPathConfig> path = clientPathService.forBranch(branch); if (path.isPresent()) { PathQuestion root = path.get().rootQuestion(); state.pathAnswers.clear(); return new CoreResponse(state.sessionId, state.visitorId, root.text(), toPathOptions(root), false, null);} state.pathMappedServiceIds.clear(); state.selectedServiceIds.clear(); state.pathAllowMultiChoice = null; return new CoreResponse(state.sessionId, state.visitorId, "Выберите услугу:", toServiceOptions(services), serviceFilter.multiServiceEnabled(branch), null); }
    private CoreResponse onPathOption(CoreSessionState state, String value) { BranchConfig branch = branchOf(state); ClientPathConfig path = clientPathService.forBranch(branch).orElseThrow(); String[] parts = value.split(":", 2); PathQuestion q = path.questions().get(parts[0]); int optionIndex = Integer.parseInt(parts[1]); PathOption option = q.options().get(optionIndex); state.pathAnswers.put(q.text(), option.text()); List<ServiceInfo> services = loadVisibleServices(branch); if (option.nextQuestionId() != null && !option.nextQuestionId().isBlank()) { PathQuestion next = path.questions().get(option.nextQuestionId()); return new CoreResponse(state.sessionId, state.visitorId, next.text(), toPathOptions(next), false, null);} List<String> serviceIds = clientPathService.optionServiceIds(option, services).stream().distinct().sorted().toList(); if (serviceIds.size() > 1 && option.multiServicesAction() == MultiServicesAction.AUTO) return finalizeVisit(state, branch, serviceIds); if (serviceIds.size() > 1) { state.pathMappedServiceIds = new HashSet<>(serviceIds); state.selectedServiceIds.clear(); state.pathAllowMultiChoice = option.multiServicesAction() == MultiServicesAction.CHOOSE_MANY; List<ServiceInfo> mapped = services.stream().filter(s -> serviceIds.contains(s.id())).toList(); return new CoreResponse(state.sessionId, state.visitorId, "По выбранному ответу доступны несколько услуг. Выберите нужную:", toServiceOptions(mapped), state.pathAllowMultiChoice, null);} return finalizeVisit(state, branch, serviceIds); }
    private CoreResponse onPathOther(CoreSessionState state, String questionId) { BranchConfig branch = branchOf(state); ClientPathConfig path = clientPathService.forBranch(branch).orElseThrow(); PathQuestion question = path.questions().get(questionId); List<ServiceInfo> services = loadVisibleServices(branch); Set<String> used = new HashSet<>(); for (PathOption option : question.options()) used.addAll(clientPathService.optionServiceIds(option, services)); List<ServiceInfo> other = services.stream().filter(s -> !used.contains(s.id())).sorted(Comparator.comparing(ServiceInfo::name)).toList(); state.pathMappedServiceIds = other.stream().map(ServiceInfo::id).collect(java.util.stream.Collectors.toSet()); state.selectedServiceIds.clear(); state.pathAllowMultiChoice = false; return new CoreResponse(state.sessionId, state.visitorId, "Выберите услугу:", toServiceOptions(other), false, null); }
    private CoreResponse onSelectService(CoreSessionState state, String serviceId) { BranchConfig branch = branchOf(state); List<ServiceInfo> services = mappedServices(state, loadVisibleServices(branch)); boolean multi = state.pathAllowMultiChoice != null ? state.pathAllowMultiChoice : serviceFilter.multiServiceEnabled(branch); if (multi && !"confirm".equals(serviceId)) { if (state.selectedServiceIds.contains(serviceId)) state.selectedServiceIds.remove(serviceId); else state.selectedServiceIds.add(serviceId); return new CoreResponse(state.sessionId, state.visitorId, "Выберите услуги и нажмите confirm", toServiceOptions(services), true, null);} List<String> selected = "confirm".equals(serviceId) ? state.selectedServiceIds.stream().sorted().toList() : List.of(serviceId); return finalizeVisit(state, branch, selected); }
    private CoreResponse finalizeVisit(CoreSessionState state, BranchConfig branch, List<String> serviceIds) { Map<String, String> answers = new HashMap<>(state.pathAnswers); answers.put("WebSessionId", state.sessionId); answers.put("WebVisitorId", state.visitorId); Map<String, Object> visit = queueGateway.createVisit(branch, serviceIds, state.visitorId, state.customerName, answers).orElseThrow(); Object ticket = visit.getOrDefault("ticketId", visit.get("ticket")); return new CoreResponse(state.sessionId, state.visitorId, "Ваш талон: " + (ticket == null ? "создан" : ticket), List.of(), false, String.valueOf(ticket)); }

    public Optional<String> sessionForVisitor(String visitorId) { return Optional.ofNullable(visitorToSession.get(visitorId)); }
    private List<ServiceInfo> loadVisibleServices(BranchConfig branch) { return serviceFilter.visibleServices(queueGateway.getServices(branch)); }
    private BranchConfig branchOf(CoreSessionState state) { return branches.byId(state.branchId).orElseThrow(); }
    private List<ServiceInfo> mappedServices(CoreSessionState state, List<ServiceInfo> services) { return state.pathMappedServiceIds.isEmpty() ? services : services.stream().filter(s -> state.pathMappedServiceIds.contains(s.id())).toList(); }
    private static List<CoreOption> toBranchOptions(List<BranchConfig> branches) { return branches.stream().map(b -> new CoreOption("select-branch:" + b.branchId(), b.name())).toList(); }
    private static List<CoreOption> toPathOptions(PathQuestion q) { List<CoreOption> out = new ArrayList<>(); for (int i = 0; i < q.options().size(); i++) out.add(new CoreOption("path-option:" + q.questionId() + ":" + i, q.options().get(i).text())); if (q.includeOtherServicesOption()) out.add(new CoreOption("path-other:" + q.questionId(), "Другое")); return out; }
    private static List<CoreOption> toServiceOptions(List<ServiceInfo> services) { List<CoreOption> options = new ArrayList<>(services.stream().map(s -> new CoreOption("select-service:" + s.id(), s.name())).toList()); options.add(new CoreOption("select-service:confirm", "Подтвердить выбор")); return options; }

    @Serdeable
    @Schema(name = "ChatInitRequest", description = "Запрос инициализации сессии чата. Позволяет передать человеко-читаемые данные пользователя (например, ФИО).")
    public record InitRequest(
            @Schema(description = "Стабильный внешний ID посетителя (если фронт его уже знает)", example = "crm-user-1024") String visitorId,
            @Schema(description = "Человеко-читаемое имя посетителя (ФИО)", example = "Иванов Иван Иванович") String customerName
    ) {}
    public record CoreAction(String type, String value, String customerName) {}
    public record CoreOption(String action, String label) {}
    public record CoreResponse(String sessionId, String visitorId, String message, List<CoreOption> options, boolean multiSelect, String ticket) {}
    private static final class CoreSessionState { private String sessionId; private String visitorId; private String branchId; private String customerName = "web-client"; private final Map<String, String> pathAnswers = new HashMap<>(); private Set<String> pathMappedServiceIds = new HashSet<>(); private final Set<String> selectedServiceIds = new HashSet<>(); private Boolean pathAllowMultiChoice; }
}
