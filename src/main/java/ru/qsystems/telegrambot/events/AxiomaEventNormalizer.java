package ru.qsystems.telegrambot.events;

import jakarta.inject.Singleton;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Singleton
public class AxiomaEventNormalizer {
    @SuppressWarnings("unchecked")
    public Optional<NormalizedVisitEvent> normalize(Map<String, Object> payload) {
        if (payload == null) {
            return Optional.empty();
        }
        String eventType = String.valueOf(payload.getOrDefault("eventType", ""));
        if (!eventType.equals("VISIT_CALLED") && !eventType.equals("VISIT_RECALLED")) {
            return Optional.empty();
        }
        Object bodyRaw = payload.get("body");
        if (!(bodyRaw instanceof Map<?, ?> bodyMapRaw)) {
            return Optional.empty();
        }
        Map<String, Object> body = (Map<String, Object>) bodyMapRaw;
        Map<String, Object> prm = new LinkedHashMap<>();
        Object parameterMap = body.get("parameterMap");
        if (parameterMap instanceof Map<?, ?> parameterRaw) {
            parameterRaw.forEach((key, value) -> {
                if (key != null) {
                    prm.put(String.valueOf(key), value);
                }
            });
        }
        if (!prm.containsKey("ticketId") && body.get("ticket") != null) {
            prm.put("ticketId", body.get("ticket"));
        }
        if (!prm.containsKey("servicePointName")) {
            Object events = body.get("events");
            if (events instanceof List<?> list && !list.isEmpty()) {
                Object last = list.get(list.size() - 1);
                if (last instanceof Map<?, ?> lastMap) {
                    Object parameters = lastMap.get("parameters");
                    if (parameters instanceof Map<?, ?> parameterValues) {
                        Object value = parameterValues.get("servicePointName");
                        if (value != null) {
                            prm.put("servicePointName", value);
                        }
                    }
                }
            }
        }

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("evnt", eventType.equals("VISIT_CALLED") ? "VISIT_CALL" : "VISIT_RECALL");
        event.put("prm", prm);
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("E", event);
        Object prefix = body.getOrDefault("branchPrefix", payload.get("branchPrefix"));
        return Optional.of(new NormalizedVisitEvent(normalized, prefix == null ? null : String.valueOf(prefix)));
    }

    public record NormalizedVisitEvent(Map<String, Object> payload, String branchPrefix) {
    }
}
