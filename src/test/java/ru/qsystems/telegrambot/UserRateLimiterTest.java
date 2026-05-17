package ru.qsystems.telegrambot;

import org.junit.jupiter.api.Test;
import ru.qsystems.telegrambot.config.BotRuntimeProperties;
import ru.qsystems.telegrambot.telegram.UserRateLimiter;

import static org.junit.jupiter.api.Assertions.*;

class UserRateLimiterTest {

    @Test
    void blocksWhenUpdatesExceedConfiguredLimit() {
        BotRuntimeProperties props = new BotRuntimeProperties();
        props.setRateLimitMaxUpdates(2);
        props.setRateLimitWindowMs(60_000);
        UserRateLimiter limiter = new UserRateLimiter(props);

        assertTrue(limiter.allow(1L));
        assertTrue(limiter.allow(1L));
        assertFalse(limiter.allow(1L));
    }

    @Test
    void allowsAgainAfterWindowIsRotated() throws Exception {
        BotRuntimeProperties props = new BotRuntimeProperties();
        props.setRateLimitMaxUpdates(1);
        props.setRateLimitWindowMs(20);
        UserRateLimiter limiter = new UserRateLimiter(props);

        assertTrue(limiter.allow(1L));
        assertFalse(limiter.allow(1L));
        Thread.sleep(30);
        assertTrue(limiter.allow(1L));
    }

    @Test
    void disabledLimiterAlwaysAllows() {
        BotRuntimeProperties props = new BotRuntimeProperties();
        props.setRateLimitMaxUpdates(0);
        props.setRateLimitWindowMs(10_000);
        UserRateLimiter limiter = new UserRateLimiter(props);

        for (int i = 0; i < 100; i++) {
            assertTrue(limiter.allow(42L));
        }
    }

    @Test
    void cleanupRemovesExpiredUsersWhenThresholdReached() {
        BotRuntimeProperties props = new BotRuntimeProperties();
        props.setRateLimitMaxUpdates(1);
        props.setRateLimitWindowMs(1);
        props.setRateLimitCleanupThreshold(2);
        UserRateLimiter limiter = new UserRateLimiter(props);

        assertTrue(limiter.allow(1L));
        assertTrue(limiter.allow(2L));
        limiter.cleanupExpired(System.currentTimeMillis() + 5);
        assertTrue(limiter.trackedUsers() <= 2);
    }

    @Test
    void cleanupThresholdLessOrEqualZeroIsSafelyNormalized() {
        BotRuntimeProperties props = new BotRuntimeProperties();
        props.setRateLimitMaxUpdates(1);
        props.setRateLimitWindowMs(10_000);
        props.setRateLimitCleanupThreshold(0);
        UserRateLimiter limiter = new UserRateLimiter(props);

        assertTrue(limiter.allow(1L));
        assertFalse(limiter.allow(1L));
        assertEquals(1, limiter.trackedUsers());
    }

    @Test
    void disabledByWindowNeverTracksUsers() {
        BotRuntimeProperties props = new BotRuntimeProperties();
        props.setRateLimitMaxUpdates(5);
        props.setRateLimitWindowMs(0);
        props.setRateLimitCleanupThreshold(10);
        UserRateLimiter limiter = new UserRateLimiter(props);

        for (int i = 0; i < 50; i++) {
            assertTrue(limiter.allow(100L + i));
        }
        assertEquals(0, limiter.trackedUsers());
    }

    @Test
    void trimsOldestTrackedUsersWhenThresholdExceededWithoutExpiredWindows() {
        BotRuntimeProperties props = new BotRuntimeProperties();
        props.setRateLimitMaxUpdates(10);
        props.setRateLimitWindowMs(60_000);
        props.setRateLimitCleanupThreshold(3);
        UserRateLimiter limiter = new UserRateLimiter(props);

        assertTrue(limiter.allow(1L));
        assertTrue(limiter.allow(2L));
        assertTrue(limiter.allow(3L));
        assertTrue(limiter.allow(4L));

        assertTrue(limiter.trackedUsers() <= 3);
    }

    @Test
    void thresholdOneKeepsSingleTrackedUserEvenWithManyUsers() {
        BotRuntimeProperties props = new BotRuntimeProperties();
        props.setRateLimitMaxUpdates(10);
        props.setRateLimitWindowMs(60_000);
        props.setRateLimitCleanupThreshold(1);
        UserRateLimiter limiter = new UserRateLimiter(props);

        for (long userId = 1; userId <= 20; userId++) {
            assertTrue(limiter.allow(userId));
        }
        assertTrue(limiter.trackedUsers() <= 1);
    }
}
