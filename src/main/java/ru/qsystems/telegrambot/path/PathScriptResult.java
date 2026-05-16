package ru.qsystems.telegrambot.path;

import java.util.List;
import java.util.Map;

public record PathScriptResult(
        String nextQuestionId,
        List<String> serviceIds,
        List<String> serviceNames,
        MultiServicesAction multiServicesAction,
        String message,
        Map<String, String> visitParameters
) {}
