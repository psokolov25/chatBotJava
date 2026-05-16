package ru.qsystems.telegrambot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.qsystems.telegrambot.config.BotRuntimeProperties;
import ru.qsystems.telegrambot.path.ScriptPackageRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ScriptPackageRegistryTest {

    @TempDir
    Path tempDir;

    @Test
    void picksNewestSemverForSameScriptId() throws Exception {
        writePackage(tempDir.resolve("pkg-1.9.0.zip"), "1.9.0", "return [message: 'old']");
        writePackage(tempDir.resolve("pkg-1.10.0.zip"), "1.10.0", "return [message: 'new']");

        BotRuntimeProperties props = new BotRuntimeProperties();
        props.setScriptPackagesDir(tempDir.toString());

        ScriptPackageRegistry registry = new ScriptPackageRegistry(props);
        ScriptPackageRegistry.ScriptDefinition def = registry.find("script-a").orElseThrow();

        assertEquals("1.10.0", def.version());
        assertTrue(def.source().contains("new"));
    }

    @Test
    void loadsMetadataAndParams() throws Exception {
        writePackage(tempDir.resolve("pkg-2.0.0.zip"), "2.0.0", "return [message: 'ok']");

        BotRuntimeProperties props = new BotRuntimeProperties();
        props.setScriptPackagesDir(tempDir.toString());

        ScriptPackageRegistry registry = new ScriptPackageRegistry(props);
        ScriptPackageRegistry.ScriptDefinition def = registry.find("script-a").orElseThrow();

        assertEquals("owner-team", def.metadata().get("owner"));
        assertEquals("VIP", ((Map<?, ?>) def.params().get("segmentDefaults")).get("level"));
    }

    private static void writePackage(Path file, String version, String script) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            put(zip, "manifest.yml", ("id: pkg\nversion: " + version + "\nscripts:\n  - id: script-a\n    path: scripts/a.groovy\n    metadata: metadata/a.yml\n    params: params/a.yml\n").getBytes(StandardCharsets.UTF_8));
            put(zip, "scripts/a.groovy", script.getBytes(StandardCharsets.UTF_8));
            put(zip, "metadata/a.yml", "owner: owner-team\n".getBytes(StandardCharsets.UTF_8));
            put(zip, "params/a.yml", "segmentDefaults:\n  level: VIP\n".getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void put(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }
}
