package ru.qsystems.telegrambot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.qsystems.telegrambot.config.BotRuntimeProperties;
import ru.qsystems.telegrambot.path.PathScriptExecutor;
import ru.qsystems.telegrambot.path.PathScriptResult;
import ru.qsystems.telegrambot.path.ScriptPackageRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class PathScriptExecutorTest {

    @TempDir
    Path tempDir;

    @Test
    void executesInlineScriptAndReturnsVisitParameters() {
        BotRuntimeProperties props = new BotRuntimeProperties();
        props.setScriptPackagesDir("/definitely/not/exist");
        PathScriptExecutor executor = new PathScriptExecutor(new ScriptPackageRegistry(props));

        PathScriptResult result = executor.execute(
                "return [message:'ok', serviceIds:['10'], visitParameters:[crmSegment:'VIP']]",
                null,
                Map.of("answer", "123")
        );

        assertNotNull(result);
        assertEquals("ok", result.message());
        assertEquals("10", result.serviceIds().get(0));
        assertEquals("VIP", result.visitParameters().get("crmSegment"));
    }
    @Test
    void cachesCompiledScriptsForRepeatedExecution() {
        BotRuntimeProperties props = new BotRuntimeProperties();
        props.setScriptPackagesDir("/definitely/not/exist");
        PathScriptExecutor executor = new PathScriptExecutor(new ScriptPackageRegistry(props));

        String script = "return [message:'ok', serviceIds:['10']]";
        executor.execute(script, null, Map.of("answer", "1"));
        executor.execute(script, null, Map.of("answer", "2"));

        assertEquals(1, executor.compiledCacheSize());
    }

    @Test
    void resolvesScriptByScriptIdAndInjectsPackageContext() throws Exception {
        writePackage(tempDir.resolve("pkg-1.0.0.zip"), "1.0.0",
                "return [message: scriptMetadata.owner + ':' + scriptParams.segmentDefaults.level + ':' + scriptVersion]");

        BotRuntimeProperties props = new BotRuntimeProperties();
        props.setScriptPackagesDir(tempDir.toString());
        PathScriptExecutor executor = new PathScriptExecutor(new ScriptPackageRegistry(props));

        PathScriptResult result = executor.execute(null, "script-a", Map.of("answer", "123"));

        assertNotNull(result);
        assertEquals("team-a:VIP:1.0.0", result.message());
    }

    private static void writePackage(Path file, String version, String script) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            put(zip, "manifest.yml", ("id: pkg\nversion: " + version + "\nscripts:\n  - id: script-a\n    path: scripts/a.groovy\n    metadata: metadata/a.yml\n    params: params/a.yml\n").getBytes(StandardCharsets.UTF_8));
            put(zip, "scripts/a.groovy", script.getBytes(StandardCharsets.UTF_8));
            put(zip, "metadata/a.yml", "owner: team-a\n".getBytes(StandardCharsets.UTF_8));
            put(zip, "params/a.yml", "segmentDefaults:\n  level: VIP\n".getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void put(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

}
