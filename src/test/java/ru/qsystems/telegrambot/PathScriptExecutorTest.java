package ru.qsystems.telegrambot;

import org.junit.jupiter.api.Test;
import ru.qsystems.telegrambot.config.BotRuntimeProperties;
import ru.qsystems.telegrambot.path.PathScriptExecutor;
import ru.qsystems.telegrambot.path.PathScriptResult;
import ru.qsystems.telegrambot.path.ScriptPackageRegistry;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PathScriptExecutorTest {

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

}
