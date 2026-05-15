package ru.qsystems.telegrambot.telegram;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class UserState {
    private StateName stateName = StateName.NONE;
    private final Map<String, Object> data = new HashMap<>();
    private final Set<String> branchSubscriptions = new HashSet<>();

    public StateName getStateName() {
        return stateName;
    }

    public void setStateName(StateName stateName) {
        this.stateName = stateName;
    }

    public Map<String, Object> data() {
        return data;
    }

    public Set<String> branchSubscriptions() {
        return branchSubscriptions;
    }

    public void clearConversation() {
        stateName = StateName.NONE;
        data.clear();
    }
}
