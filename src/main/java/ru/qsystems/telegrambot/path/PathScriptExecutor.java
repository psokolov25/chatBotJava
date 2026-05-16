package ru.qsystems.telegrambot.path;

import jakarta.inject.Singleton;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.SimpleBindings;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class PathScriptExecutor {
    private static final int MAX_COMPILED_CACHE = 500;

    private final ScriptPackageRegistry packageRegistry;
    private final ConcurrentHashMap<String, RuntimeContext> runtimeCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompiledScript> compiledCache = new ConcurrentHashMap<>();

    public PathScriptExecutor(ScriptPackageRegistry packageRegistry) {
        this.packageRegistry = packageRegistry;
    }

    public PathScriptResult execute(String inlineScript, String scriptId, Map<String, Object> context) {
        String script = inlineScript;
        Map<String, Object> runtimeContext = new LinkedHashMap<>(context);
        List<ScriptPackageRegistry.DependencyJar> dependencies = List.of();
        if ((script == null || script.isBlank()) && scriptId != null && !scriptId.isBlank()) {
            ScriptPackageRegistry.ScriptDefinition def = packageRegistry.find(scriptId).orElse(null);
            if (def != null) {
                script = def.source();
                runtimeContext.put("scriptMetadata", def.metadata());
                runtimeContext.put("scriptParams", def.params());
                runtimeContext.put("scriptVersion", def.version());
                dependencies = def.dependencies();
            }
        }
        if (script == null || script.isBlank()) return null;
        try {
            if (dependencies == null || dependencies.isEmpty()) {
                String depKey = "nodeps";
                final String finalScript = script;
                RuntimeContext rc = runtimeCache.computeIfAbsent(depKey, ignored -> buildRuntimeContext(List.of()));
                String scriptKey = depKey + ":" + sha256(script);
                CompiledScript compiled = compiledCache.computeIfAbsent(scriptKey, ignored -> compile(rc.engine(), finalScript));
                trimCompiledCacheIfNeeded();
                Object raw = compiled.eval(new SimpleBindings(runtimeContext));
                if (!(raw instanceof Map<?, ?> map)) return null;
                return new PathScriptResult(
                        str(map.get("nextQuestionId")),
                        stringList(map.get("serviceIds")),
                        stringList(map.get("serviceNames")),
                        MultiServicesAction.from(str(map.get("multiServicesAction"))),
                        str(map.get("message")),
                        stringMap(map.get("visitParameters"))
                );
            }

            ClassLoader depCl = buildDependencyClassLoader(dependencies);
            ClassLoader previous = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(depCl);
            try {
                ScriptEngine engine = new ScriptEngineManager(depCl).getEngineByName("groovy");
                if (engine == null) throw new IllegalStateException("Groovy script engine is not available. Add groovy-jsr223 dependency.");
                Object raw = engine.eval(script, new SimpleBindings(runtimeContext));
                if (!(raw instanceof Map<?, ?> map)) return null;
                return new PathScriptResult(
                        str(map.get("nextQuestionId")),
                        stringList(map.get("serviceIds")),
                        stringList(map.get("serviceNames")),
                        MultiServicesAction.from(str(map.get("multiServicesAction"))),
                        str(map.get("message")),
                        stringMap(map.get("visitParameters"))
                );
            } finally {
                Thread.currentThread().setContextClassLoader(previous);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Path Groovy script execution failed", e);
        }
    }

    public int compiledCacheSize() { return compiledCache.size(); }

    public int runtimeCacheSize() { return runtimeCache.size(); }


    private void trimCompiledCacheIfNeeded() {
        if (compiledCache.size() <= MAX_COMPILED_CACHE) return;
        int toRemove = compiledCache.size() - MAX_COMPILED_CACHE;
        var it = compiledCache.keySet().iterator();
        while (toRemove > 0 && it.hasNext()) {
            it.next();
            it.remove();
            toRemove--;
        }
    }

    private static CompiledScript compile(ScriptEngine engine, String script) {
        if (!(engine instanceof Compilable compilable)) {
            throw new IllegalStateException("Groovy engine does not support Compilable");
        }
        try {
            return compilable.compile(script);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compile Groovy script", e);
        }
    }

    private RuntimeContext buildRuntimeContext(List<ScriptPackageRegistry.DependencyJar> dependencies) {
        try {
            ClassLoader cl = buildDependencyClassLoader(dependencies);
            ScriptEngine engine = new ScriptEngineManager(cl).getEngineByName("groovy");
            if (engine == null) throw new IllegalStateException("Groovy script engine is not available. Add groovy-jsr223 dependency.");
            return new RuntimeContext(engine);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize script runtime", e);
        }
    }

    private ClassLoader buildDependencyClassLoader(List<ScriptPackageRegistry.DependencyJar> dependencies) throws Exception {
        if (dependencies == null || dependencies.isEmpty()) {
            return Thread.currentThread().getContextClassLoader();
        }
        Path tempDir = Files.createTempDirectory("script-deps-");
        tempDir.toFile().deleteOnExit();
        List<URL> urls = new java.util.ArrayList<>();
        for (ScriptPackageRegistry.DependencyJar dep : dependencies) {
            Path p = tempDir.resolve(Path.of(dep.path()).getFileName().toString());
            Files.write(p, dep.content());
            p.toFile().deleteOnExit();
            urls.add(p.toUri().toURL());
        }
        return new URLClassLoader(urls.toArray(URL[]::new), Thread.currentThread().getContextClassLoader());
    }

    private static String dependencyKey(List<ScriptPackageRegistry.DependencyJar> dependencies) {
        if (dependencies == null || dependencies.isEmpty()) return "nodeps";
        StringBuilder sb = new StringBuilder();
        for (ScriptPackageRegistry.DependencyJar dep : dependencies) {
            sb.append(dep.path()).append(':').append(sha256(dep.content())).append(';');
        }
        return sha256(sb.toString());
    }

    private static String sha256(String data) {
        return sha256(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(md.digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private record RuntimeContext(ScriptEngine engine) {}

    private static String str(Object value) { return value == null ? null : value.toString(); }
    private static List<String> stringList(Object value) { return value instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of(); }
    private static Map<String, String> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        Map<String, String> out = new LinkedHashMap<>();
        map.forEach((k, v) -> { if (k != null && v != null) out.put(String.valueOf(k), String.valueOf(v)); });
        return out;
    }
}
