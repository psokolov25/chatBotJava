package ru.qsystems.telegrambot.model;

public record BranchConfig(
        String branchId,
        String name,
        String prefix,
        String entryPointId,
        QueueSystem queueSystem,
        String baseUrl,
        String login,
        String password,
        String visitCallTemplate
) {
    public BranchConfig withVisitCallTemplate(String template) {
        return new BranchConfig(branchId, name, prefix, entryPointId, queueSystem, baseUrl, login, password, template);
    }
}
