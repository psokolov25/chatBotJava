package ru.qsystems.telegrambot.path;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record CustomPathElement(String id, String title, String scriptId, String description) {
}
