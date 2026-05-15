package ru.qsystems.telegrambot.events;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.qsystems.telegrambot.config.BranchConfigurationService;
import ru.qsystems.telegrambot.model.BranchConfig;
import ru.qsystems.telegrambot.telegram.TelegramApiClient;
import ru.qsystems.telegrambot.telegram.UserStateStore;
import ru.qsystems.telegrambot.util.PiiSanitizer;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Singleton
public class VisitCallEventDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(VisitCallEventDispatcher.class);
    private static final Set<String> SUPPORTED_EVENTS = Set.of("VISIT_CALL", "VISIT_RECALL");

    private final TelegramApiClient telegramApiClient;
    private final UserStateStore userStateStore;
    private final BranchConfigurationService branches;
    private final VisitMessageRenderer renderer;
    private final PiiSanitizer sanitizer;

    public VisitCallEventDispatcher(
            TelegramApiClient telegramApiClient,
            UserStateStore userStateStore,
            BranchConfigurationService branches,
            VisitMessageRenderer renderer,
            PiiSanitizer sanitizer
    ) {
        this.telegramApiClient = telegramApiClient;
        this.userStateStore = userStateStore;
        this.branches = branches;
        this.renderer = renderer;
        this.sanitizer = sanitizer;
    }

    @SuppressWarnings("unchecked")
    public void dispatch(Map<String, Object> data, String branchPrefix) {
        Object eventRaw = data == null ? null : data.get("E");
        if (!(eventRaw instanceof Map<?, ?> eventMapRaw)) {
            return;
        }
        Map<String, Object> event = (Map<String, Object>) eventMapRaw;
        String eventType = String.valueOf(event.getOrDefault("evnt", ""));
        if (!SUPPORTED_EVENTS.contains(eventType)) {
            return;
        }
        Object prmRaw = event.get("prm");
        if (!(prmRaw instanceof Map<?, ?> prmMapRaw)) {
            return;
        }
        Map<String, Object> prm = (Map<String, Object>) prmMapRaw;
        LOG.info("{} payload: {}", eventType, sanitizer.sanitize(prm));

        Object chatRaw = prm.getOrDefault("TelegramChatId", prm.get("TelegramCustomerId"));
        if (chatRaw == null) {
            return;
        }
        long chatId;
        try {
            chatId = Long.parseLong(String.valueOf(chatRaw));
        } catch (NumberFormatException e) {
            LOG.warn("VISIT_CALL skipped: TelegramChatId/TelegramCustomerId is not numeric ({})", chatRaw);
            return;
        }

        Set<String> allowedPrefixes = userStateStore.branchSubscriptions(chatId);
        if (branchPrefix != null && !allowedPrefixes.isEmpty() && !allowedPrefixes.contains(branchPrefix)) {
            return;
        }

        Optional<BranchConfig> branch = branchPrefix == null ? Optional.empty() : branches.byPrefix(branchPrefix);
        if (branch.isEmpty() && branches.branches().size() == 1) {
            branch = Optional.of(branches.branches().get(0));
        }
        if (branch.isEmpty()) {
            return;
        }

        String message = renderer.render(branch.get().visitCallTemplate(), prm, event);
        telegramApiClient.sendMessage(chatId, message, null);
    }
}
