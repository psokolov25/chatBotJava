package ru.qsystems.telegrambot.events;

import jakarta.inject.Singleton;
import ru.qsystems.telegrambot.config.BotRuntimeProperties;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class VisitMessageRenderer {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_.-]+)}");
    private final BotRuntimeProperties properties;

    public VisitMessageRenderer(BotRuntimeProperties properties) {
        this.properties = properties;
    }

    public String render(String branchTemplate, Map<String, Object> prm, Map<String, Object> event) {
        String template = branchTemplate == null || branchTemplate.isBlank()
                ? properties.getVisitCallTemplate()
                : branchTemplate;
        Map<String, Object> data = new HashMap<>();
        if (event != null) {
            data.putAll(event);
        }
        if (prm != null) {
            data.putAll(prm);
            alias(prm, data, "TelegramCustomerId", "visitorId");
            alias(prm, data, "TelegramCustomerFullName", "visitorName");
            alias(prm, data, "ticketId", "ticket");
            alias(prm, data, "servicePointName", "servicePointId");
        }
        try {
            Matcher matcher = PLACEHOLDER.matcher(template);
            StringBuilder result = new StringBuilder();
            while (matcher.find()) {
                String key = matcher.group(1);
                Object value = data.get(key);
                matcher.appendReplacement(result, Matcher.quoteReplacement(value == null ? "{" + key + "}" : String.valueOf(value)));
            }
            matcher.appendTail(result);
            return result.toString();
        } catch (Exception e) {
            return properties.getVisitCallTemplate();
        }
    }

    private static void alias(Map<String, Object> prm, Map<String, Object> data, String source, String target) {
        if (prm.containsKey(source) && !data.containsKey(target)) {
            data.put(target, prm.get(source));
        }
    }
}
