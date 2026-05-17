package ru.qsystems.telegrambot;

import org.junit.jupiter.api.Test;
import ru.qsystems.telegrambot.api.ClientPathAdminController;
import ru.qsystems.telegrambot.config.BotRuntimeProperties;
import ru.qsystems.telegrambot.path.CustomPathElement;
import ru.qsystems.telegrambot.path.CustomPathElementService;
import ru.qsystems.telegrambot.path.ScriptArchiveStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ClientPathAdminControllerTest {
    @Test
    void saveScriptArchiveWritesZipWithSanitizedName() throws Exception {
        Path temp = Files.createTempDirectory("pkg-test");
        BotRuntimeProperties props = new BotRuntimeProperties();
        props.setScriptPackagesDir(temp.toString());
        CustomPathElementService service = new CustomPathElementService();
        ScriptArchiveStore archiveStore = mock(ScriptArchiveStore.class);
        ClientPathAdminController controller = new ClientPathAdminController(service, props, archiveStore);
        String payload = Base64.getEncoder().encodeToString("demo".getBytes());

        Map<String, Object> response = controller.saveScriptArchive(new ClientPathAdminController.SaveArchiveRequest("../x.zip", payload));

        assertEquals("saved", response.get("status"));
        verify(archiveStore).saveArchive(eq("x.zip"), any(), any());
    }

    @Test
    void createAndDeleteElement() {
        CustomPathElementService service = new CustomPathElementService();
        BotRuntimeProperties props = new BotRuntimeProperties();
        ScriptArchiveStore archiveStore = mock(ScriptArchiveStore.class);
        ClientPathAdminController controller = new ClientPathAdminController(service, props, archiveStore);
        CustomPathElement element = new CustomPathElement("x","X","prereg-pin-check","d");

        CustomPathElement saved = controller.createOrUpdateElement(element);
        assertEquals("x", saved.id());
        assertTrue(controller.listElements().stream().anyMatch(it -> "x".equals(it.id())));
        assertEquals(Boolean.TRUE, controller.deleteElement("x").get("deleted"));
    }
}
