package ru.qsystems.telegrambot.path;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class CustomPathElementService {
    private static final Logger LOG = LoggerFactory.getLogger(CustomPathElementService.class);
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private final Path storagePath = Path.of("custom_path_elements.yml");
    private final Map<String, CustomPathElement> elements = new LinkedHashMap<>();

    public CustomPathElementService() {
        load();
    }

    public synchronized List<CustomPathElement> list() {
        return List.copyOf(elements.values());
    }

    public synchronized CustomPathElement save(CustomPathElement element) {
        if (element == null || isBlank(element.id()) || isBlank(element.title()) || isBlank(element.scriptId())) {
            throw new IllegalArgumentException("id, title and scriptId are required");
        }
        elements.put(element.id(), element);
        persist();
        return element;
    }

    public synchronized boolean delete(String id) {
        boolean removed = elements.remove(id) != null;
        if (removed) {
            persist();
        }
        return removed;
    }

    private void load() {
        if (!Files.exists(storagePath)) return;
        try {
            Map<String, Object> root = YAML.readValue(storagePath.toFile(), new TypeReference<>() {});
            Object rawList = root.get("elements");
            if (rawList instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        String id = text(map.get("id"));
                        String title = text(map.get("title"));
                        String scriptId = text(map.get("script_id"));
                        String description = text(map.get("description"));
                        if (!id.isBlank() && !title.isBlank() && !scriptId.isBlank()) {
                            elements.put(id, new CustomPathElement(id, title, scriptId, description));
                        }
                    }
                }
            }
            LOG.info("Loaded custom path elements: {}", elements.keySet());
        } catch (IOException e) {
            LOG.warn("Failed to load custom path elements from {}", storagePath, e);
        }
    }

    private void persist() {
        List<Map<String, Object>> raw = new ArrayList<>();
        for (CustomPathElement element : elements.values()) {
            raw.add(Map.of(
                    "id", element.id(),
                    "title", element.title(),
                    "script_id", element.scriptId(),
                    "description", element.description() == null ? "" : element.description()
            ));
        }
        try {
            YAML.writeValue(storagePath.toFile(), Map.of("elements", raw));
            LOG.info("Saved custom path elements file: {} ({} elements)", storagePath, raw.size());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist custom path elements", e);
        }
    }

    private static String text(Object value) { return value == null ? "" : value.toString().trim(); }
    private static boolean isBlank(String v) { return v == null || v.isBlank(); }
}
