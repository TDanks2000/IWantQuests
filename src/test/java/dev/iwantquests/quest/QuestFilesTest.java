package dev.iwantquests.quest;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QuestFilesTest {
    @TempDir
    Path root;

    @Test
    void missingDirectoryCreatesAnEmptyCatalogueSource() throws Exception {
        Path directory = root.resolve("quests");
        assertTrue(QuestFiles.loadDirectory(directory).isEmpty());
        assertTrue(Files.isDirectory(directory));
    }

    @Test
    void nestedUtf8FilesLoadAndNonJsonFilesAreIgnored() throws Exception {
        Files.createDirectories(root.resolve("chapters"));
        Files.writeString(root.resolve("chapters/mining.json"), """
            {"chapters":[{"id":"creator:mining","title":"Mineração","icon":"minecraft:stone"}],"quests":[]}
            """);
        Files.writeString(root.resolve("README.txt"), "Not quest JSON");
        var packs = QuestFiles.loadDirectory(root);
        assertEquals(1, packs.size());
        assertEquals("Mineração", packs.getFirst().chapters().getFirst().title());
        assertTrue(new QuestCatalog(packs.getFirst(), (kind, target) -> {})
                .quests()
                .isEmpty());
    }

    @Test
    void invalidFileNamesItsPathAndRejectsTheWholeLoad() throws Exception {
        Files.writeString(root.resolve("valid.json"), "{\"chapters\":[],\"quests\":[]}");
        Files.writeString(root.resolve("broken.json"), "{\"chapters\":[]}");
        var error = assertThrows(IllegalArgumentException.class, () -> QuestFiles.loadDirectory(root));
        assertTrue(error.getMessage().contains("broken.json"));
        assertTrue(error.getMessage().contains("Expected chapters and quests arrays"));
    }
}
