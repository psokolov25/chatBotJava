package ru.qsystems.telegrambot.path;

import java.util.List;

public record PathOption(
        String text,
        String nextQuestionId,
        List<String> serviceIds,
        List<String> serviceNames,
        MultiServicesAction multiServicesAction
) {
}
