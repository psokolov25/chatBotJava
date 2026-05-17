package ru.qsystems.telegrambot.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.runtime.event.ApplicationShutdownEvent;
import io.micronaut.runtime.event.ApplicationStartupEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class TelegramPollingService implements ApplicationEventListener<ApplicationStartupEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(TelegramPollingService.class);

    private final TelegramApiClient telegramApiClient;
    private final TelegramUpdateHandler updateHandler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService updateExecutor = new ThreadPoolExecutor(
            2,
            8,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1_000),
            r -> {
                Thread thread = new Thread(r, "telegram-update-worker");
                thread.setDaemon(false);
                return thread;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "telegram-polling");
        thread.setDaemon(false);
        return thread;
    });

    public TelegramPollingService(TelegramApiClient telegramApiClient, TelegramUpdateHandler updateHandler) {
        this.telegramApiClient = telegramApiClient;
        this.updateHandler = updateHandler;
    }

    @Override
    public void onApplicationEvent(ApplicationStartupEvent event) {
        if (!telegramApiClient.enabled()) {
            LOG.warn("Telegram polling is disabled or API_TOKEN is empty");
            return;
        }
        running.set(true);
        executorService.submit(this::pollLoop);
        LOG.info("Telegram long polling started");
    }

    @io.micronaut.runtime.event.annotation.EventListener
    void onShutdown(ApplicationShutdownEvent event) {
        running.set(false);
        executorService.shutdownNow();
        updateExecutor.shutdownNow();
        try {
            executorService.awaitTermination(10, TimeUnit.SECONDS);
            updateExecutor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void pollLoop() {
        long offset = 0;
        long retryDelayMs = 1_000;
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                JsonNode updates = telegramApiClient.getUpdates(offset);
                retryDelayMs = 1_000;
                if (updates.isArray()) {
                    for (JsonNode update : updates) {
                        offset = Math.max(offset, update.path("update_id").asLong() + 1);
                        dispatchUpdate(update);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LOG.warn("Telegram polling failed, retry in {}ms: {}", retryDelayMs, e.getMessage(), e);
                sleep(retryDelayMs);
                retryDelayMs = Math.min(retryDelayMs * 2, 30_000);
            }
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void dispatchUpdate(JsonNode update) {
        try {
            updateExecutor.submit(() -> {
                try {
                    updateHandler.handle(update);
                } catch (Exception e) {
                    LOG.warn("Ошибка обработки обновления Telegram: {}", normalizeMessage(e.getMessage()), e);
                }
            });
        } catch (Exception e) {
            LOG.warn("Ошибка диспетчеризации обновления Telegram: {}", normalizeMessage(e.getMessage()), e);
        }
    }

    private static String normalizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return message;
        }
        String fixed = repairMojibake(message, Charset.forName("IBM866"));
        if (!fixed.equals(message)) {
            return fixed;
        }
        return repairMojibake(message, StandardCharsets.ISO_8859_1);
    }

    private static String repairMojibake(String source, Charset wrongCharset) {
        if (source.indexOf('╨') < 0 && source.indexOf('╤') < 0) {
            return source;
        }
        String decoded = new String(source.getBytes(wrongCharset), StandardCharsets.UTF_8);
        return hasCyrillic(decoded) ? decoded : source;
    }

    private static boolean hasCyrillic(String value) {
        for (int i = 0; i < value.length(); i++) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(value.charAt(i));
            if (block == Character.UnicodeBlock.CYRILLIC || block == Character.UnicodeBlock.CYRILLIC_SUPPLEMENTARY
                    || block == Character.UnicodeBlock.CYRILLIC_EXTENDED_A || block == Character.UnicodeBlock.CYRILLIC_EXTENDED_B) {
                return true;
            }
        }
        return false;
    }
}
