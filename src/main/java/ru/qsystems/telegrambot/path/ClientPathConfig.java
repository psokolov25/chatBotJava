package ru.qsystems.telegrambot.path;

import java.util.Map;

public record ClientPathConfig(
        String rootQuestionId,
        Map<String, PathQuestion> questions
) {
    public PathQuestion rootQuestion() {
        return questions.get(rootQuestionId);
    }
}
