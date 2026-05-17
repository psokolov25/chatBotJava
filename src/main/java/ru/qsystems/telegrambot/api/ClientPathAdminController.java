package ru.qsystems.telegrambot.api;

import io.micronaut.http.annotation.*;
import io.micronaut.serde.annotation.Serdeable;
import ru.qsystems.telegrambot.config.BotRuntimeProperties;
import ru.qsystems.telegrambot.path.CustomPathElement;
import ru.qsystems.telegrambot.path.CustomPathElementService;
import ru.qsystems.telegrambot.path.ScriptArchiveStore;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Controller("/api/bot/admin/client-path")
public class ClientPathAdminController {
    private final CustomPathElementService customPathElementService;
    private final BotRuntimeProperties runtimeProperties;
    private final ScriptArchiveStore scriptArchiveStore;

    public ClientPathAdminController(CustomPathElementService customPathElementService, BotRuntimeProperties runtimeProperties, ScriptArchiveStore scriptArchiveStore) {
        this.customPathElementService = customPathElementService;
        this.runtimeProperties = runtimeProperties;
        this.scriptArchiveStore = scriptArchiveStore;
    }

    @Get("/elements")
    public List<CustomPathElement> listElements() {
        return customPathElementService.list();
    }

    @Post("/elements")
    public CustomPathElement createOrUpdateElement(@Body CustomPathElement element) {
        return customPathElementService.save(element);
    }

    @Delete("/elements/{id}")
    public Map<String, Object> deleteElement(String id) {
        return Map.of("deleted", customPathElementService.delete(id), "id", id);
    }

    @Post("/script-archives")
    public Map<String, Object> saveScriptArchive(@Body SaveArchiveRequest request) throws IOException {
        if (request == null || request.fileName() == null || request.fileName().isBlank() || request.base64() == null || request.base64().isBlank()) {
            throw new IllegalArgumentException("fileName and base64 are required");
        }
        Path dir = Path.of(runtimeProperties.getScriptPackagesDir());
        String safeFileName = Path.of(request.fileName()).getFileName().toString();
        if (!safeFileName.endsWith(".zip")) {
            throw new IllegalArgumentException("Only .zip archives are supported");
        }
        Path target = dir.resolve(safeFileName);
        byte[] content = Base64.getDecoder().decode(request.base64());
        Instant now = Instant.now();
        scriptArchiveStore.saveArchive(safeFileName, content, now);
        org.slf4j.LoggerFactory.getLogger(ClientPathAdminController.class)
                .info("Saved script archive to H2: {} ({} bytes) at {}", safeFileName, content.length, now);
        return Map.of("status", "saved", "path", target.toString(), "fileName", safeFileName, "bytes", content.length, "storage", "h2");
    }

    @Serdeable
    public record SaveArchiveRequest(String fileName, String base64) {}
}
