package ru.qsystems.telegrambot.path;

import java.util.List;
import java.util.Map;

public record ClientPathConfig(
        String rootQuestionId,
        Map<String, PathQuestion> questions,
        List<EntryAction> entryActions
) {
    public ClientPathConfig(String rootQuestionId, Map<String, PathQuestion> questions) {
        this(rootQuestionId, questions, List.of(new EntryAction("take-ticket", "Взять талон", rootQuestionId)));
    }

    public PathQuestion rootQuestion() {
        return questions.get(rootQuestionId);
    }
}
