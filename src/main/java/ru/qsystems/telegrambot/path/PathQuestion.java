package ru.qsystems.telegrambot.path;

import java.util.List;

public record PathQuestion(
        String questionId,
        String text,
        List<PathOption> options,
        boolean includeOtherServicesOption
) {
}
