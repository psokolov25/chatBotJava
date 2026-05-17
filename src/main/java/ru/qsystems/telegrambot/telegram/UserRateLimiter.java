package ru.qsystems.telegrambot.telegram;

import jakarta.inject.Singleton;
import ru.qsystems.telegrambot.config.BotRuntimeProperties;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Singleton
public class UserRateLimiter {
    private final ConcurrentMap<Long, WindowState> windows = new ConcurrentHashMap<>();
    private final int maxUpdatesPerWindow;
    private final long windowMillis;
    private final int cleanupThreshold;

    public UserRateLimiter(BotRuntimeProperties runtimeProperties) {
        this.maxUpdatesPerWindow = runtimeProperties.getRateLimitMaxUpdates();
        this.windowMillis = runtimeProperties.getRateLimitWindowMs();
        this.cleanupThreshold = Math.max(1, runtimeProperties.getRateLimitCleanupThreshold());
    }

    public boolean allow(long userId) {
        if (maxUpdatesPerWindow <= 0 || windowMillis <= 0) return true;
        long now = System.currentTimeMillis();
        if (windows.size() >= cleanupThreshold) {
            cleanupExpired(now);
            trimToThreshold();
        }
        WindowState state = windows.compute(userId, (ignored, current) -> {
            if (current == null || now - current.windowStartMs >= windowMillis) {
                return new WindowState(now, 1);
            }
            return new WindowState(current.windowStartMs, current.count + 1);
        });
        if (windows.size() > cleanupThreshold) {
            trimToThreshold();
        }
        return state.count <= maxUpdatesPerWindow;
    }

    public int trackedUsers() {
        return windows.size();
    }

    public void cleanupExpired(long now) {
        windows.entrySet().removeIf(entry -> now - entry.getValue().windowStartMs >= windowMillis);
    }

    private void trimToThreshold() {
        int overflow = windows.size() - cleanupThreshold;
        if (overflow <= 0) {
            return;
        }
        windows.entrySet().stream()
                .sorted(java.util.Comparator.comparingLong(entry -> entry.getValue().windowStartMs))
                .limit(overflow)
                .map(java.util.Map.Entry::getKey)
                .toList()
                .forEach(windows::remove);
    }

    private record WindowState(long windowStartMs, int count) {
    }
}
