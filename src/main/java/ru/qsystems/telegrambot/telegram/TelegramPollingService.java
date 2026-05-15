package ru.qsystems.telegrambot.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.runtime.event.ApplicationShutdownEvent;
import io.micronaut.runtime.event.ApplicationStartupEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class TelegramPollingService implements ApplicationEventListener<ApplicationStartupEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(TelegramPollingService.class);

    private final TelegramApiClient telegramApiClient;
    private final TelegramUpdateHandler updateHandler;
    private final AtomicBoolean running = new AtomicBoolean(false);
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
        try {
            executorService.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void pollLoop() {
        long offset = 0;
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                JsonNode updates = telegramApiClient.getUpdates(offset);
                if (updates.isArray()) {
                    for (JsonNode update : updates) {
                        offset = Math.max(offset, update.path("update_id").asLong() + 1);
                        updateHandler.handle(update);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LOG.warn("Telegram polling failed, retry in 3s: {}", e.getMessage(), e);
                sleep(3000);
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
}
