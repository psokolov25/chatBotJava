package ru.qsystems.telegrambot;

import org.junit.jupiter.api.Test;
import ru.qsystems.telegrambot.config.BotRuntimeProperties;
import ru.qsystems.telegrambot.events.VisitMessageRenderer;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VisitMessageRendererTest {
    @Test
    void rendersKnownPlaceholdersAndKeepsUnknownPlaceholders() {
        BotRuntimeProperties properties = new BotRuntimeProperties();
        properties.setVisitCallTemplate("Талон {ticketId}, окно {servicePointId}");
        VisitMessageRenderer renderer = new VisitMessageRenderer(properties);

        String text = renderer.render(
                "Событие {evnt}, талон {ticketId}, окно {servicePointId}, x={unknown}",
                Map.of("ticketId", "Д012"),
                Map.of("evnt", "VISIT_CALL")
        );

        assertEquals("Событие VISIT_CALL, талон Д012, окно {servicePointId}, x={unknown}", text);
    }

    @Test
    void createsAliasesForVisitorAndTicket() {
        BotRuntimeProperties properties = new BotRuntimeProperties();
        VisitMessageRenderer renderer = new VisitMessageRenderer(properties);

        String text = renderer.render(
                "{visitorName}: {ticket}",
                Map.of("TelegramCustomerFullName", "Иван", "ticketId", "A001"),
                Map.of()
        );

        assertEquals("Иван: A001", text);
    }
}
