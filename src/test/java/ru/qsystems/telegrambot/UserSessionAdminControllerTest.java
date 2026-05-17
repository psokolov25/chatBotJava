package ru.qsystems.telegrambot;

import org.junit.jupiter.api.Test;
import ru.qsystems.telegrambot.api.UserSessionAdminController;
import ru.qsystems.telegrambot.telegram.UserStateStore;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UserSessionAdminControllerTest {

    @Test
    void resetSessionRemovesStateForUser() {
        UserStateStore store = new UserStateStore();
        store.get(100L).data().put("branch_id", "x");
        UserSessionAdminController controller = new UserSessionAdminController(store);

        Map<String, Object> response = controller.resetSession(new UserSessionAdminController.ResetSessionRequest(100L));

        assertEquals("ok", response.get("status"));
        assertEquals(100L, response.get("userId"));
        assertTrue(store.branchId(100L).isEmpty());
    }

    @Test
    void resetSessionRejectsInvalidUserId() {
        UserSessionAdminController controller = new UserSessionAdminController(new UserStateStore());

        assertThrows(IllegalArgumentException.class,
                () -> controller.resetSession(new UserSessionAdminController.ResetSessionRequest(0L)));
    }

    @Test
    void resetSessionRejectsNullRequestOrUserId() {
        UserSessionAdminController controller = new UserSessionAdminController(new UserStateStore());

        assertThrows(IllegalArgumentException.class, () -> controller.resetSession(null));
        assertThrows(IllegalArgumentException.class,
                () -> controller.resetSession(new UserSessionAdminController.ResetSessionRequest(null)));
    }
}
