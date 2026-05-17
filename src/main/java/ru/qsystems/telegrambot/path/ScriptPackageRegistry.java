package ru.qsystems.telegrambot.path;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.micronaut.context.annotation.Context;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.qsystems.telegrambot.config.BotRuntimeProperties;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Context
@Singleton
public class ScriptPackageRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(ScriptPackageRegistry.class);
    private final Map<String, ScriptDefinition> scripts;

    public ScriptPackageRegistry(BotRuntimeProperties properties, ScriptArchiveStore archiveStore) {
        this.scripts = load(Path.of(properties.getScriptPackagesDir()), archiveStore);
    }

    public ScriptPackageRegistry(BotRuntimeProperties properties) {
        this.scripts = load(Path.of(properties.getScriptPackagesDir()), null);
    }

    public Optional<ScriptDefinition> find(String scriptId) {
        return Optional.ofNullable(scripts.get(scriptId));
    }

    private Map<String, ScriptDefinition> load(Path dir, ScriptArchiveStore archiveStore) {
        try {
            Map<String, ScriptDefinition> out = new LinkedHashMap<>();
            if (archiveStore != null) {
                for (ScriptArchiveStore.ArchiveRecord record : archiveStore.findAll()) {
                    loadZipBytes(record.fileName(), record.content()).forEach((k, v) -> out.merge(k, v, this::pickNewest));
                }
            }
            if (out.isEmpty() && Files.isDirectory(dir)) {
                List<Path> zips = Files.list(dir).filter(p -> p.getFileName().toString().endsWith(".zip")).sorted().toList();
                for (Path zip : zips) loadZip(zip).forEach((k, v) -> out.merge(k, v, this::pickNewest));
            }
            LOG.info("Loaded script packages: {} scripts", out.size());
            return out;
        } catch (IOException e) {
            LOG.warn("Failed to scan script package dir: {}", dir, e);
            return Map.of();
        }
    }

    private Map<String, ScriptDefinition> loadZip(Path zipPath) {
        try (InputStream in = Files.newInputStream(zipPath)) {
            return loadZipStream(zipPath.getFileName().toString(), in);
        } catch (Exception ex) {
            LOG.warn("Failed to load script package zip stream: {}", zipPath, ex);
            return Map.of();
        }
    }

    private Map<String, ScriptDefinition> loadZipBytes(String name, byte[] data) {
        try (InputStream in = new java.io.ByteArrayInputStream(data)) {
            return loadZipStream(name, in);
        } catch (Exception ex) {
            LOG.warn("Failed to load script package zip from H2: {}", name, ex);
            return Map.of();
        }
    }

    private Map<String, ScriptDefinition> loadZipStream(String packageName, InputStream in) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zin = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (!e.isDirectory()) entries.put(e.getName(), zin.readAllBytes());
            }
            Map<String, Object> manifest = entries.containsKey("manifest.yml")
                    ? mapper.readValue(entries.get("manifest.yml"), new TypeReference<>() {}) : Map.of();
            String version = String.valueOf(manifest.getOrDefault("version", "0.0.0"));
            List<Map<String, Object>> scriptsMeta = manifest.get("scripts") instanceof List<?> list
                    ? (List<Map<String, Object>>) (List<?>) list : List.of();
            Map<String, ScriptDefinition> out = new LinkedHashMap<>();
            for (Map<String, Object> meta : scriptsMeta) {
                String id = String.valueOf(meta.getOrDefault("id", "")).trim();
                String path = String.valueOf(meta.getOrDefault("path", "")).trim();
                if (id.isBlank() || path.isBlank() || !entries.containsKey(path)) continue;
                String source = new String(entries.get(path), StandardCharsets.UTF_8);
                Map<String, Object> metadata = loadYaml(mapper, entries.getOrDefault(String.valueOf(meta.getOrDefault("metadata", "")), null));
                Map<String, Object> params = loadYaml(mapper, entries.getOrDefault(String.valueOf(meta.getOrDefault("params", "")), null));
                List<String> depPaths = dependencyPaths(meta, entries);
                List<DependencyJar> dependencyJars = depPaths.stream()
                        .filter(entries::containsKey)
                        .map(dep -> new DependencyJar(dep, entries.get(dep)))
                        .collect(Collectors.toList());
                out.put(id, new ScriptDefinition(id, version, source, metadata, params, packageName, dependencyJars));
            }
            return out;
        } catch (Exception ex) {
            LOG.warn("Failed to load script package zip stream: {}", packageName, ex);
            return Map.of();
        }
    }


    @SuppressWarnings("unchecked")
    private static List<String> dependencyPaths(Map<String, Object> meta, Map<String, byte[]> entries) {
        Object deps = meta.get("dependencies");
        if (deps instanceof List<?> list && !list.isEmpty()) {
            return list.stream().map(String::valueOf).toList();
        }
        return entries.keySet().stream().filter(k -> k.startsWith("lib/") && k.endsWith(".jar")).sorted().toList();
    }
    private Map<String, Object> loadYaml(ObjectMapper mapper, byte[] data) {
        if (data == null || data.length == 0) return Map.of();
        try { return mapper.readValue(data, new TypeReference<>() {}); } catch (Exception e) { return Map.of(); }
    }

    private ScriptDefinition pickNewest(ScriptDefinition a, ScriptDefinition b) {
        return compareVersions(a.version(), b.version()) >= 0 ? a : b;
    }

    static int compareVersions(String left, String right) {
        List<Integer> l = parseVersion(left);
        List<Integer> r = parseVersion(right);
        int size = Math.max(l.size(), r.size());
        for (int i = 0; i < size; i++) {
            int lv = i < l.size() ? l.get(i) : 0;
            int rv = i < r.size() ? r.get(i) : 0;
            if (lv != rv) return Integer.compare(lv, rv);
        }
        return 0;
    }

    private static List<Integer> parseVersion(String version) {
        if (version == null || version.isBlank()) return List.of(0);
        String normalized = version.trim().split("[-+]", 2)[0];
        List<Integer> parts = new ArrayList<>();
        for (String part : normalized.split("\\.")) {
            try {
                parts.add(Integer.parseInt(part));
            } catch (NumberFormatException ignored) {
                parts.add(0);
            }
        }
        return parts.isEmpty() ? List.of(0) : parts;
    }

    public record ScriptDefinition(String id, String version, String source, Map<String, Object> metadata, Map<String, Object> params, String packageName, List<DependencyJar> dependencies) {}

    public record DependencyJar(String path, byte[] content) {}
}
