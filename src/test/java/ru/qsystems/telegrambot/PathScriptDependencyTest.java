package ru.qsystems.telegrambot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.qsystems.telegrambot.config.BotRuntimeProperties;
import ru.qsystems.telegrambot.path.PathScriptExecutor;
import ru.qsystems.telegrambot.path.PathScriptResult;
import ru.qsystems.telegrambot.path.ScriptPackageRegistry;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class PathScriptDependencyTest {
    @TempDir Path tempDir;

    @Test
    void executesScriptUsingJarDependencyFromPackage() throws Exception {
        Path depJar = createDependencyJar(tempDir.resolve("dep.jar"));
        Path pkg = tempDir.resolve("pkg.zip");
        writePackageWithDep(pkg, Files.readAllBytes(depJar));

        BotRuntimeProperties props = new BotRuntimeProperties();
        props.setScriptPackagesDir(tempDir.toString());
        PathScriptExecutor executor = new PathScriptExecutor(new ScriptPackageRegistry(props));

        PathScriptResult result = executor.execute(null, "dep-script", Map.of("answer", "x"));
        assertNotNull(result);
        assertEquals("from-dep", result.message());
    }

    private static Path createDependencyJar(Path jarPath) throws IOException {
        Path srcDir = Files.createTempDirectory("dep-src");
        Path pkgDir = srcDir.resolve("pkg");
        Files.createDirectories(pkgDir);
        Path src = pkgDir.resolve("Helper.java");
        Files.writeString(src, "package pkg; public class Helper { public static String value(){ return \"from-dep\"; } }");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required for this test");
        int rc = compiler.run(null, null, null, src.toString());
        assertEquals(0, rc);
        Path cls = pkgDir.resolve("Helper.class");
        try (JarOutputStream jout = new JarOutputStream(Files.newOutputStream(jarPath))) {
            jout.putNextEntry(new JarEntry("pkg/Helper.class"));
            jout.write(Files.readAllBytes(cls));
            jout.closeEntry();
        }
        return jarPath;
    }

    private static void writePackageWithDep(Path zipPath, byte[] jarBytes) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            put(zip, "manifest.yml", ("id: p\nversion: 1.0.0\nscripts:\n  - id: dep-script\n    path: scripts/use_dep.groovy\n    dependencies:\n      - lib/dep.jar\n").getBytes(StandardCharsets.UTF_8));
            put(zip, "scripts/use_dep.groovy", "import pkg.Helper\nreturn [message: Helper.value()]".getBytes(StandardCharsets.UTF_8));
            put(zip, "lib/dep.jar", jarBytes);
        }
    }

    private static void put(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }
}
