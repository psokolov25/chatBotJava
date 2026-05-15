package ru.qsystems.telegrambot;

import io.micronaut.runtime.Micronaut;

/**
 * Bootstrap-класс Micronaut приложения.
 */
public final class Application {
    private Application() {
    }

    /**
     * Запускает Micronaut-контекст и HTTP/фоновые сервисы приложения.
     *
     * @param args аргументы командной строки JVM.
     */
    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }
}
