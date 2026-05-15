package ru.qsystems.telegrambot.telegram;

import jakarta.inject.Singleton;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Singleton
public class UserStateStore {
    private final ConcurrentMap<Long, UserState> states = new ConcurrentHashMap<>();

    public UserState get(long userId) {
        return states.computeIfAbsent(userId, ignored -> new UserState());
    }

    public void addBranchSubscription(long userId, String branchPrefix) {
        get(userId).branchSubscriptions().add(branchPrefix);
    }

    public Set<String> branchSubscriptions(long userId) {
        return Set.copyOf(get(userId).branchSubscriptions());
    }

    public Optional<String> branchId(long userId) {
        Object value = get(userId).data().get("branch_id");
        return value == null ? Optional.empty() : Optional.of(value.toString());
    }
}
