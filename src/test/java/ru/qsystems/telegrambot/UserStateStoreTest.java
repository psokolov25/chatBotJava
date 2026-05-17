package ru.qsystems.telegrambot;

import org.junit.jupiter.api.Test;
import ru.qsystems.telegrambot.telegram.UserState;
import ru.qsystems.telegrambot.telegram.UserStateStore;

import static org.junit.jupiter.api.Assertions.*;

class UserStateStoreTest {

    @Test
    void resetRemovesStoredConversationDataAndSubscriptions() {
        UserStateStore store = new UserStateStore();
        long userId = 101L;

        UserState state = store.get(userId);
        state.data().put("branch_id", "b-1");
        store.addBranchSubscription(userId, "br");

        store.reset(userId);

        UserState reloaded = store.get(userId);
        assertTrue(reloaded.data().isEmpty());
        assertTrue(store.branchSubscriptions(userId).isEmpty());
        assertTrue(store.branchId(userId).isEmpty());
    }
}
